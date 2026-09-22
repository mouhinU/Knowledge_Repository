package com.mouhin.knowledge.repository.infrastructure.extraction;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Service;

/**
 * PowerPoint 文档提取服务（PPTX / PPT）
 *
 * <p>使用 Apache POI 按幻灯片提取，每张幻灯片一个 section。
 *
 * @author mouhinU
 * @date 2026-09-22 16:21:33
 */
@Service
@Slf4j
public class PptxExtractionService {

    /**
     * 从 PowerPoint 文件提取文本
     *
     * @param filePath PowerPoint 文件路径
     * @return 提取结果
     * @throws IOException 读取失败
     */
    public ExtractionResult extract(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath);
                XMLSlideShow slideShow = new XMLSlideShow(is)) {

            List<XSLFSlide> slides = slideShow.getSlides();
            List<String> slideTexts = new ArrayList<>(slides.size());

            for (int i = 0; i < slides.size(); i++) {
                slideTexts.add(buildSlideText(slides.get(i), i));
            }

            if (slideTexts.isEmpty()) {
                slideTexts.add("");
            }

            log.info("PowerPoint extracted: {} slides", slides.size());
            return new ExtractionResult(
                    slideTexts,
                    slides.size(),
                    false,
                    ExtractionSupport.calculateChecksum(filePath),
                    "pptx");
        }
    }

    private String buildSlideText(XSLFSlide slide, int slideIndex) {
        StringBuilder slideContent = new StringBuilder();
        slideContent.append("[Slide ").append(slideIndex + 1).append("]\n");

        slide.getShapes()
                .forEach(
                        shape -> {
                            if (shape instanceof XSLFTextShape textShape) {
                                String text = textShape.getText();
                                if (text != null && !text.isBlank()) {
                                    slideContent.append(text.trim()).append("\n");
                                }
                            }
                        });

        return slideContent.toString().trim();
    }
}
