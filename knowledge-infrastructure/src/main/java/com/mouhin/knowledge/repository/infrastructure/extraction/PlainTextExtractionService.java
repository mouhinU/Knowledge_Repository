package com.mouhin.knowledge.repository.infrastructure.extraction;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 纯文本文档提取服务（TXT / CSV / HTML / Markdown）
 *
 * <p>按段落分割纯文本文件。
 *
 * @author mouhinU
 * @date 2026-09-22 16:21:33
 */
@Service
@Slf4j
public class PlainTextExtractionService {

    /**
     * 从纯文本文件提取文本
     *
     * @param filePath 文件路径
     * @param mimeType MIME 类型（用于日志）
     * @return 提取结果
     * @throws IOException 读取失败
     */
    public ExtractionResult extract(Path filePath, String mimeType) throws IOException {
        String fullText = Files.readString(filePath).trim();

        List<String> sections = splitByParagraphs(fullText);

        if (sections.isEmpty()) {
            sections.add(fullText.isEmpty() ? "" : fullText);
        }

        log.info(
                "Plain text extraction ({}): {} sections, {} chars",
                mimeType,
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
