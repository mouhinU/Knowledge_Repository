package com.mouhin.knowledge.repository.infrastructure.extraction;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
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
import org.springframework.stereotype.Service;

/**
 * Tika 通用回退提取服务
 *
 * <p>当 MIME 类型不匹配任何专用策略时，使用 Apache Tika AutoDetectParser 兜底。
 *
 * @author beginningness
 * @date 2026-09-22 16:21:33
 */
@Service
@Slf4j
public class TikaFallbackExtractionService {

    /**
     * 使用 Tika 通用解析提取文本
     *
     * @param filePath 文件路径
     * @param mimeType MIME 类型（用于日志 + Tika 提示）
     * @return 提取结果
     * @throws IOException 解析失败
     */
    public ExtractionResult extract(Path filePath, String mimeType) throws IOException {
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
                    mimeType,
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
