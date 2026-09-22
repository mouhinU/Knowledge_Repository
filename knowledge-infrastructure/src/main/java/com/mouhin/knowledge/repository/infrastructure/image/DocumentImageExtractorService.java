package com.mouhin.knowledge.repository.infrastructure.image;

import com.mouhin.knowledge.repository.domain.gateway.DocumentImageExtractorGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractedImage;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

/**
 * 文档内嵌图片提取服务（基础设施层）。
 *
 * <p>与文本提取服务平行，从 PDF / Word(.docx) / Excel(.xlsx) / PowerPoint(.pptx) 中抽取位图：
 *
 * <ul>
 *   <li>PDF：PDFBox 逐页遍历 {@code /XObject} 图像资源，按对象身份跨页去重；
 *   <li>Word：POI 按正文顺序（段落 / 表格）解析内联图片，保留出现次序与近似页序；
 *   <li>Excel / PPT：POI 遍历工作表 / 幻灯片中的图片。
 * </ul>
 *
 * 过滤过小装饰图（默认 {@code >=80x80}）并限制单文档上限，异常按格式吞掉、绝不中断文本摄入主流程。 仅返回二进制与元信息，落盘与持久化由应用层完成。
 *
 * @author mouhinU
 * @date 2026-09-20
 */
@Service
@Slf4j
public class DocumentImageExtractorService implements DocumentImageExtractorGateway {

    /** 过滤边长小于该值的装饰性小图（px） */
    private static final int MIN_EDGE = 80;

    /** 单文档最多抽取的图片数，防御恶意 / 超大文件撑爆内存 */
    private static final int MAX_IMAGES_PER_DOC = 300;

    /** 默认 / fallback MIME（java:S1192：抽常量防 5 处 MIME_PNG 字面量漂移）。 */
    private static final String MIME_PNG = "image/png";

    private final Tika tika = new Tika();

    @Override
    public List<ExtractedImage> extractImages(Path filePath, String fileName) throws IOException {
        if (filePath == null) {
            return List.of();
        }
        // 破 javasecurity:S6549 filesystem oracle：先归一为绝对路径 + 拒显式 ".." 段，
        // 调用方（DocumentImageSupport）已在应用层把 sourcePath 约束到 assetRoot/storageRoot 白名单，
        // 本 infra 方法仅探测归一后的路径存在性，不再直接接收未净化输入。
        Path sanitized = filePath.toAbsolutePath().normalize();
        if (filePath.toString().contains("..") || sanitized.toString().contains("..")) {
            return List.of();
        }
        if (!Files.exists(sanitized)) { // NOSONAR java:S6549 canonicalized + upstream allowlist
            return List.of();
        }
        String mimeType;
        try {
            mimeType = tika.detect(sanitized);
        } catch (Exception e) {
            return List.of();
        }
        log.info("Extracting images from: {} (type={})", fileName, mimeType);
        try {
            List<ExtractedImage> images =
                    switch (mimeType) {
                        case "application/pdf" -> extractFromPdf(sanitized);
                        case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                                extractFromDocx(sanitized);
                        case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                                extractFromXlsx(sanitized);
                        case "application/vnd.openxmlformats-officedocument.presentationml.presentation" ->
                                extractFromPptx(sanitized);
                        default -> List.of();
                    };
            log.info("Image extraction done: {} -> {} image(s)", fileName, images.size());
            return images;
        } catch (Exception e) {
            log.warn("Image extraction failed for {} ({}): {}", fileName, mimeType, e.getMessage());
            return List.of();
        }
    }

    // ==================== PDF ====================

    private List<ExtractedImage> extractFromPdf(Path path) throws IOException {
        List<ExtractedImage> result = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            Set<COSBase> seen = new HashSet<>();
            int pageNo = 0;
            for (PDPage page : doc.getPages()) {
                pageNo++;
                PDResources resources = page.getResources();
                if (resources == null) {
                    continue;
                }
                int seq = 0;
                for (COSName name : resources.getXObjectNames()) {
                    PDXObject xobj;
                    try {
                        xobj = resources.getXObject(name);
                    } catch (Exception e) {
                        continue;
                    }
                    if (!(xobj instanceof PDImageXObject image)) {
                        continue;
                    }
                    COSBase obj = image.getCOSObject();
                    if (obj != null && !seen.add(obj)) {
                        continue;
                    }
                    BufferedImage awt;
                    try {
                        awt = image.getImage();
                    } catch (Exception e) {
                        continue;
                    }
                    if (accept(awt) && result.size() < MAX_IMAGES_PER_DOC) {
                        byte[] bytes = toPng(awt);
                        result.add(
                                new ExtractedImage(
                                        bytes,
                                        MIME_PNG,
                                        awt.getWidth(),
                                        awt.getHeight(),
                                        pageNo,
                                        seq++));
                    }
                }
            }
        }
        return result;
    }

    // ==================== Word ====================

    private List<ExtractedImage> extractFromDocx(Path path) throws IOException {
        List<ExtractedImage> result = new ArrayList<>();
        try (InputStream is = Files.newInputStream(path);
                XWPFDocument doc = new XWPFDocument(is)) {

            // 按正文顺序采集内联图片（含表格），无法定位时回退 getAllPictures
            List<DocxPic> collected = new ArrayList<>();
            int[] elementCounter = {0};
            collectDocxPictures(doc.getBodyElements(), collected, elementCounter);
            if (collected.isEmpty()) {
                for (XWPFPictureData data : doc.getAllPictures()) {
                    collected.add(new DocxPic(data, 1));
                }
            }

            int seq = 0;
            for (DocxPic pic : collected) {
                if (result.size() >= MAX_IMAGES_PER_DOC) {
                    break;
                }
                XWPFPictureData data = pic.data();
                ExtractedImage img =
                        tryBuildImage(
                                data.getData(),
                                data.getPackagePart() != null
                                        ? data.getPackagePart().getContentType()
                                        : null,
                                data.suggestFileExtension(),
                                pic.page(),
                                seq);
                if (img != null) {
                    result.add(img);
                    seq++;
                }
            }
        }
        return result;
    }

    private void collectDocxPictures(
            List<IBodyElement> bodyElements, List<DocxPic> out, int[] counter) {
        if (bodyElements == null) {
            return;
        }
        for (IBodyElement el : bodyElements) {
            if (out.size() >= MAX_IMAGES_PER_DOC) {
                return;
            }
            if (el instanceof XWPFParagraph para) {
                counter[0]++;
                int approxPage = counter[0] / 50 + 1;
                for (XWPFRun run : para.getRuns()) {
                    for (XWPFPicture picture : run.getEmbeddedPictures()) {
                        XWPFPictureData data = picture.getPictureData();
                        if (data != null) {
                            out.add(new DocxPic(data, approxPage));
                        }
                    }
                }
            } else if (el instanceof XWPFTable table) {
                table.getRows()
                        .forEach(
                                row ->
                                        row.getTableCells()
                                                .forEach(
                                                        cell ->
                                                                collectDocxPictures(
                                                                        cell.getBodyElements(),
                                                                        out,
                                                                        counter)));
            }
        }
    }

    // ==================== Excel ====================

    private List<ExtractedImage> extractFromXlsx(Path path) throws IOException {
        List<ExtractedImage> result = new ArrayList<>();
        try (InputStream is = Files.newInputStream(path);
                XSSFWorkbook wb = new XSSFWorkbook(is)) {

            List<XSSFPictureData> pictures = wb.getAllPictures();
            int sheetPages = Math.max(1, wb.getNumberOfSheets());
            int seq = 0;
            for (int i = 0; i < pictures.size(); i++) {
                if (result.size() >= MAX_IMAGES_PER_DOC) {
                    break;
                }
                XSSFPictureData data = pictures.get(i);
                // xlsx 图片与工作表映射不精确，按数量分摊估算页序
                int approxSheet =
                        (int) Math.floor((double) i / Math.max(1, pictures.size()) * sheetPages)
                                + 1;
                ExtractedImage img =
                        tryBuildImage(
                                data.getData(),
                                data.getPackagePart() != null
                                        ? data.getPackagePart().getContentType()
                                        : null,
                                data.suggestFileExtension(),
                                approxSheet,
                                seq);
                if (img != null) {
                    result.add(img);
                    seq++;
                }
            }
        }
        return result;
    }

    // ==================== PowerPoint ====================

    private List<ExtractedImage> extractFromPptx(Path path) throws IOException {
        List<ExtractedImage> result = new ArrayList<>();
        try (InputStream is = Files.newInputStream(path);
                XMLSlideShow ppt = new XMLSlideShow(is)) {

            int slideNo = 0;
            for (XSLFSlide slide : ppt.getSlides()) {
                slideNo++;
                int seq = 0;
                for (XSLFShape shape : slide.getShapes()) {
                    if (result.size() >= MAX_IMAGES_PER_DOC) {
                        break;
                    }
                    if (shape instanceof XSLFPictureShape pictureShape) {
                        XSLFPictureData data = pictureShape.getPictureData();
                        if (data == null) {
                            continue;
                        }
                        byte[] bytes = data.getData();
                        ExtractedImage img =
                                tryBuildImage(
                                        bytes,
                                        data.getPackagePart() != null
                                                ? data.getPackagePart().getContentType()
                                                : null,
                                        null,
                                        slideNo,
                                        seq);
                        if (img != null) {
                            result.add(img);
                            seq++;
                        }
                    }
                }
            }
        }
        return result;
    }

    // ==================== 工具 ====================

    /**
     * docx / xlsx / pptx 共享的「原始图片字节 → {@link ExtractedImage}」骨架。
     *
     * <p>解码后按 {@link #accept} 过滤装饰性小图：不通过则返回 {@code null}，调用方据此跳过且不推进 {@code seq}；通过则以 {@link
     * #resolveMime} 定 MIME 并封装尺寸 / 页序 / 序号。accept 已保证 BufferedImage 非空，可直接取宽高。
     *
     * @param bytes 图片原始字节
     * @param contentType 包内声明的内容类型（可为 null）
     * @param fileExt 建议文件扩展名（pptx 无扩展名，传 null）
     * @param pageNo 页序 / 幻灯片序号 / 工作表估算页
     * @param seq 该页内图片序号
     * @return 过滤通过的 {@link ExtractedImage}；被判定为装饰小图或解码失败时返回 {@code null}
     */
    private ExtractedImage tryBuildImage(
            byte[] bytes, String contentType, String fileExt, int pageNo, int seq) {
        BufferedImage awt = readImage(bytes);
        if (!accept(awt)) {
            return null;
        }
        return new ExtractedImage(
                bytes,
                resolveMime(bytes, contentType, fileExt),
                awt.getWidth(),
                awt.getHeight(),
                pageNo,
                seq);
    }

    private record DocxPic(XWPFPictureData data, int page) {}

    private boolean accept(BufferedImage img) {
        return img != null && img.getWidth() >= MIN_EDGE && img.getHeight() >= MIN_EDGE;
    }

    private BufferedImage readImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] toPng(BufferedImage img) throws IOException {
        BufferedImage rgb = img;
        if (img.getType() != BufferedImage.TYPE_INT_RGB
                && img.getType() != BufferedImage.TYPE_INT_ARGB) {
            rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(rgb, "png", baos);
        return baos.toByteArray();
    }

    /** 解析图片 MIME：优先内容类型（image/*），其次文件扩展名，最后按字节魔数嗅探。 */
    private String resolveMime(byte[] bytes, String contentType, String fileExt) {
        if (contentType != null) {
            String lower = contentType.toLowerCase(Locale.ROOT);
            if (lower.startsWith("image/")) {
                return lower;
            }
        }
        if (fileExt != null) {
            String byExt =
                    switch (fileExt.toLowerCase(Locale.ROOT)) {
                        case "png" -> MIME_PNG;
                        case "jpg", "jpeg" -> "image/jpeg";
                        case "gif" -> "image/gif";
                        case "bmp" -> "image/bmp";
                        case "tif", "tiff" -> "image/tiff";
                        default -> null;
                    };
            if (byExt != null) {
                return byExt;
            }
        }
        return sniff(bytes);
    }

    private String sniff(byte[] b) {
        if (b == null || b.length < 4) {
            return MIME_PNG;
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) {
            return MIME_PNG;
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F') {
            return "image/gif";
        }
        if (b.length >= 12
                && b[0] == 'R'
                && b[1] == 'I'
                && b[2] == 'F'
                && b[3] == 'F'
                && b[8] == 'W'
                && b[9] == 'E'
                && b[10] == 'B'
                && b[11] == 'P') {
            return "image/webp";
        }
        if ((b[0] & 0xFF) == 0x42 && (b[1] & 0xFF) == 0x4D) {
            return "image/bmp";
        }
        return MIME_PNG;
    }
}
