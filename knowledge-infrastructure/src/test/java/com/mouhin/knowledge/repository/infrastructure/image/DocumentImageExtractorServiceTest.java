package com.mouhin.knowledge.repository.infrastructure.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 文档图片提取服务回归测试。
 *
 * <p>用 ImageIO 现造一张 300×200 的 PNG，注入 {@link XWPFDocument} 内联到段落，落盘为 docx， 再让 {@link
 * DocumentImageExtractorService} 从磁盘解析回图片，验证：识别到一张、MIME 正确、 尺寸与源图匹配；空文档返回空列表不抛异常。
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
@DisplayName("文档图片提取服务")
class DocumentImageExtractorServiceTest {

    private final DocumentImageExtractorService service = new DocumentImageExtractorService();

    @Test
    @DisplayName("DOCX 内联一张 PNG → 提取到该图（尺寸 + MIME 正确）")
    void extractPngFromDocx(@TempDir Path tmp) throws Exception {
        byte[] png = makePng(300, 200, Color.BLUE);
        Path docx = tmp.resolve("with-image.docx");
        try (XWPFDocument doc = new XWPFDocument();
                OutputStream os = Files.newOutputStream(docx)) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun run = p.createRun();
            run.setText("看图写话");
            run.addPicture(
                    new java.io.ByteArrayInputStream(png),
                    XWPFDocument.PICTURE_TYPE_PNG,
                    "img.png",
                    300,
                    200);
            doc.write(os);
        }

        List<ExtractedImage> images = service.extractImages(docx, "with-image.docx");

        assertThat(images).as("docx 内联 PNG 应被抽取到").hasSize(1);
        ExtractedImage img = images.get(0);
        assertThat(img.mimeType()).isEqualTo("image/png");
        assertThat(img.width()).isEqualTo(300);
        assertThat(img.height()).isEqualTo(200);
        assertThat(img.bytes()).isNotEmpty();
    }

    @Test
    @DisplayName("过滤过小装饰图（边长 < 80px 不计入结果）")
    void tinyImageFiltered(@TempDir Path tmp) throws Exception {
        byte[] tiny = makePng(20, 20, Color.RED);
        Path docx = tmp.resolve("only-tiny.docx");
        try (XWPFDocument doc = new XWPFDocument();
                OutputStream os = Files.newOutputStream(docx)) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun()
                    .addPicture(
                            new java.io.ByteArrayInputStream(tiny),
                            XWPFDocument.PICTURE_TYPE_PNG,
                            "dot.png",
                            20,
                            20);
            doc.write(os);
        }

        assertThat(service.extractImages(docx, "only-tiny.docx")).as("小于阈值的装饰性小图应被过滤").isEmpty();
    }

    @Test
    @DisplayName("文件不存在 / 无图片格式 → 空列表且不抛异常")
    void missingFileReturnsEmpty(@TempDir Path tmp) throws Exception {
        Path absent = tmp.resolve("not-there.docx");
        assertThat(service.extractImages(absent, "not-there.docx")).isEmpty();

        Path txt = tmp.resolve("plain.txt");
        Files.writeString(txt, "no images here");
        assertThat(service.extractImages(txt, "plain.txt")).as("非图片承载格式返回空列表").isEmpty();
    }

    private static byte[] makePng(int w, int h, Color color) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
