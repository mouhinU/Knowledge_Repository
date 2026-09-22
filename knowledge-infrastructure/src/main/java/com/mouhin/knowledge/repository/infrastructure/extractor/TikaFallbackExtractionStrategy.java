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
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

/**
 * Tika 通用兜底解析策略。
 *
 * <p>当 MIME 类型不匹配任何专用策略时，使用 Apache Tika {@link AutoDetectParser} 兜底。 {@link
 * #supports(ExtractionCandidate)} 恒为 {@code true}，配合最低优先级充当路由栈末端。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
@Slf4j
public class TikaFallbackExtractionStrategy implements ContentExtractor {

    @Override
    public String name() {
        return ExtractionStrategyEnum.TIKA_FALLBACK.name();
    }

    @Override
    public int priority() {
        return 60;
    }

    @Override
    public boolean supports(ExtractionCandidate candidate) {
        return candidate != null;
    }

    @Override
    public ExtractionResult extract(ExtractionCandidate candidate) throws IOException {
        Path filePath = candidate.filePath();
        try (InputStream is = Files.newInputStream(filePath)) {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filePath.getFileName().toString());

            parser.parse(is, handler, metadata);
            String fullText = handler.toString().trim();

            List<String> sections = splitByParagraphs(fullText);

            if (sections.isEmpty()) {
                sections.add(fullText.isEmpty() ? "" : fullText);
            }

            log.info(
                    "Generic extraction ({}): {} sections, {} chars",
                    candidate.mimeType(),
                    sections.size(),
                    fullText.length());
            return new ExtractionResult(
                    sections,
                    sections.size(),
                    false,
                    ExtractionSupport.calculateChecksum(filePath),
                    "generic");
        } catch (Exception e) {
            throw new IOException("Failed to parse file with Tika: " + e.getMessage(), e);
        }
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
