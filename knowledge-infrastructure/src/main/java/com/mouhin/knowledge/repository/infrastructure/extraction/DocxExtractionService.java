package com.mouhin.knowledge.repository.infrastructure.extraction;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

/**
 * Word 文档提取服务（DOCX / DOC）
 *
 * <p>使用 Apache POI 按段落提取，每 30 段切一个 section 近似页面。
 *
 * @author beginningness
 * @date 2026-09-22 16:21:33
 */
@Service
@Slf4j
public class DocxExtractionService {

    /** 每 section 最大段落数（近似页面） */
    private static final int PARAGRAPHS_PER_SECTION = 30;

    /**
     * 从 Word 文件提取文本
     *
     * @param filePath Word 文件路径
     * @return 提取结果
     * @throws IOException 读取失败
     */
    public ExtractionResult extract(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath);
                XWPFDocument document = new XWPFDocument(is)) {

            List<XWPFParagraph> paragraphs = document.getParagraphs();
            List<String> sections = buildSections(paragraphs);

            log.info(
                    "Word document extracted: {} paragraphs, {} sections",
                    paragraphs.size(),
                    sections.size());
            return new ExtractionResult(
                    sections,
                    sections.size(),
                    false,
                    ExtractionSupport.calculateChecksum(filePath),
                    "docx");
        }
    }

    private List<String> buildSections(List<XWPFParagraph> paragraphs) {
        List<String> sections = new ArrayList<>();
        StringBuilder currentSection = new StringBuilder();

        for (XWPFParagraph para : paragraphs) {
            String text = para.getText();
            if (text == null || text.isBlank()) {
                if (currentSection.length() > 0) {
                    currentSection.append("\n");
                }
                continue;
            }

            currentSection.append(text.trim()).append("\n");

            if (currentSection.toString().split("\n").length > PARAGRAPHS_PER_SECTION) {
                sections.add(currentSection.toString().trim());
                currentSection = new StringBuilder();
            }
        }

        if (currentSection.length() > 0) {
            sections.add(currentSection.toString().trim());
        }

        if (sections.isEmpty()) {
            sections.add("");
        }
        return sections;
    }
}
