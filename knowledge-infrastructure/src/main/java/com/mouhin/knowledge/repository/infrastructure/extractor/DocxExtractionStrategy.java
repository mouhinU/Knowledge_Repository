package com.mouhin.knowledge.repository.infrastructure.extractor;

import com.mouhin.knowledge.repository.domain.gateway.ContentExtractor;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionStrategyEnum;
import com.mouhin.knowledge.repository.infrastructure.extraction.ExtractionSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

/**
 * Word 解析策略（DOCX / DOC）。
 *
 * <p>使用 Apache POI 按段落提取，每 {@value #PARAGRAPHS_PER_SECTION} 段切一个 section 近似页面。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
@Slf4j
public class DocxExtractionStrategy implements ContentExtractor {

    /** 每 section 最大段落数（近似页面）。 */
    private static final int PARAGRAPHS_PER_SECTION = 30;

    /** 本策略命中的 MIME 类型。 */
    private static final Set<String> SUPPORTED_MIME_TYPES =
            Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/msword");

    @Override
    public String name() {
        return ExtractionStrategyEnum.DOCX.name();
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean supports(ExtractionCandidate candidate) {
        return candidate != null && SUPPORTED_MIME_TYPES.contains(candidate.mimeType());
    }

    @Override
    public ExtractionResult extract(ExtractionCandidate candidate) throws IOException {
        Path filePath = candidate.filePath();
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
