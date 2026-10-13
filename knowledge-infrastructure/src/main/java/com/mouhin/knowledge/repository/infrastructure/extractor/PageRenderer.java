package com.mouhin.knowledge.repository.infrastructure.extractor;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

/**
 * PDF 页面栅格化工具（Phase B）。
 *
 * <p>当文档无可用嵌入图（或嵌入图质量不足）时，用 PDFBox 3 {@link PDFRenderer} 以指定 DPI 将页面渲染为 PNG 字节， 供 {@link
 * VisionModelExtractionStrategy} 转成 data-URI 送入视觉模型。每次调用独立开关 {@link PDDocument}， 与既有 {@link
 * com.mouhin.knowledge.repository.infrastructure.pdf.EnhancedPdfTextExtractor} 的加载方式一致。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
@Slf4j
public class PageRenderer {

    private static final String PNG = "png";
    private static final int DEFAULT_DPI = 150;

    /**
     * 渲染指定页为 PNG 字节。
     *
     * @param pdfPath PDF 文件路径
     * @param pageIndex 页索引（0 起始）
     * @param dpi 分辨率（&lt;=0 时回退 150）
     * @return PNG 图像字节
     * @throws IOException 读取或渲染失败
     */
    public byte[] renderPageToPng(Path pdfPath, int pageIndex, int dpi) throws IOException {
        int effectiveDpi = dpi > 0 ? dpi : DEFAULT_DPI;
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            int pages = document.getNumberOfPages();
            if (pageIndex < 0 || pageIndex >= pages) {
                throw new IOException("页索引越界: pageIndex=" + pageIndex + ", totalPages=" + pages);
            }
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image =
                    renderer.renderImageWithDPI(pageIndex, effectiveDpi, ImageType.RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, PNG, out);
            byte[] bytes = out.toByteArray();
            log.debug(
                    "Rendered PDF page {} of {} @{}dpi -> {} bytes",
                    pageIndex,
                    pages,
                    effectiveDpi,
                    bytes.length);
            return bytes;
        }
    }

    /**
     * 返回 PDF 总页数。
     *
     * @param pdfPath PDF 文件路径
     * @return 页数
     * @throws IOException 读取失败
     */
    public int pageCount(Path pdfPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            return document.getNumberOfPages();
        }
    }
}
