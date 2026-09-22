package com.mouhin.knowledge.repository.infrastructure.extractor;

import com.mouhin.knowledge.repository.domain.gateway.ContentExtractor;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionStrategyEnum;
import com.mouhin.knowledge.repository.infrastructure.extraction.ExtractionSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 纯文本族解析策略（TXT / CSV / HTML / Markdown）。
 *
 * <p>按空行分割段落，每段一个 section。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
@Slf4j
public class PlainTextExtractionStrategy implements ContentExtractor {

    /** 本策略命中的 MIME 类型。 */
    private static final Set<String> SUPPORTED_MIME_TYPES =
            Set.of("text/plain", "text/csv", "text/html", "text/markdown");

    @Override
    public String name() {
        return ExtractionStrategyEnum.PLAIN_TEXT.name();
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public boolean supports(ExtractionCandidate candidate) {
        return candidate != null && SUPPORTED_MIME_TYPES.contains(candidate.mimeType());
    }

    @Override
    public ExtractionResult extract(ExtractionCandidate candidate) throws IOException {
        Path filePath = candidate.filePath();
        String fullText = Files.readString(filePath).trim();

        List<String> sections = splitByParagraphs(fullText);

        if (sections.isEmpty()) {
            sections.add(fullText.isEmpty() ? "" : fullText);
        }

        log.info(
                "Plain text extraction ({}): {} sections, {} chars",
                candidate.mimeType(),
                sections.size(),
                fullText.length());
        return new ExtractionResult(
                sections,
                sections.size(),
                false,
                ExtractionSupport.calculateChecksum(filePath),
                "text");
    }

    private List<String> splitByParagraphs(String fullText) {
        List<String> sections = new ArrayList<>();
        if (fullText.isEmpty()) {
            return sections;
        }
        String[] paragraphs = fullText.split("\\n\\s*\\n");
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (!trimmed.isEmpty()) {
                sections.add(trimmed);
            }
        }
        return sections;
    }
}
