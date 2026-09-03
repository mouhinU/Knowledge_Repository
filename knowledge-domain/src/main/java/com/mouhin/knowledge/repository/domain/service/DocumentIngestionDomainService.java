package com.mouhin.knowledge.repository.domain.service;

import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档摄入领域服务
 * <p>
 * 负责将提取的文本按配置进行分块，并为每个分块附加权限元数据。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Service
public class DocumentIngestionDomainService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentIngestionDomainService.class);

    /**
     * 将文本内容按配置分块，并附加文档权限元数据
     *
     * @param document 文档聚合根
     * @param pages    按页提取的文本列表（index 0 = page 1）
     * @param config   分块配置
     * @return 分块列表
     */
    public List<DocumentChunk> chunkDocument(Document document, List<String> pages, ChunkingConfig config) {
        if (pages == null || pages.isEmpty()) {
            logger.warn("Document {} has no pages to chunk", document.getDocumentKey());
            return List.of();
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        if (config.isRespectPageBoundary()) {
            // 按页面分块：每页独立处理，超长页面再按 token 切分
            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                String pageText = pages.get(pageIndex);
                if (pageText == null || pageText.isBlank()) {
                    continue;
                }

                int pageNum = pageIndex + 1;
                List<String> subChunks = splitByTokenLimit(pageText, config);

                for (String subChunk : subChunks) {
                    DocumentChunk chunk = buildChunk(document, chunkIndex, pageNum, pageNum, subChunk);
                    chunks.add(chunk);
                    chunkIndex++;
                }
            }
        } else {
            // 跨页面分块：将所有页面文本合并后按 token 限制切分
            StringBuilder fullText = new StringBuilder();
            for (String pageText : pages) {
                if (pageText != null && !pageText.isBlank()) {
                    if (!fullText.isEmpty()) {
                        fullText.append("\n\n");
                    }
                    fullText.append(pageText);
                }
            }

            List<String> subChunks = splitByTokenLimit(fullText.toString(), config);
            for (String subChunk : subChunks) {
                DocumentChunk chunk = buildChunk(document, chunkIndex, 1, pages.size(), subChunk);
                chunks.add(chunk);
                chunkIndex++;
            }
        }

        logger.info("Document {} chunked into {} pieces", document.getDocumentKey(), chunks.size());
        return chunks;
    }

    /**
     * 按 token 限制切分文本，支持段落边界和重叠
     */
    private List<String> splitByTokenLimit(String text, ChunkingConfig config) {
        List<String> result = new ArrayList<>();

        if (config.isRespectParagraphBoundary()) {
            // 先按段落分割
            String[] paragraphs = text.split("\\n\\s*\\n");
            StringBuilder current = new StringBuilder();

            for (String paragraph : paragraphs) {
                String trimmed = paragraph.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                // 估算追加后的 token 数
                String candidate = !current.isEmpty()
                        ? current + "\n\n" + trimmed
                        : trimmed;
                int estimatedTokens = estimateTokenCount(candidate);

                if (estimatedTokens > config.getMaxChunkSize() && !current.isEmpty()) {
                    // 当前块已满，保存并开始新块
                    result.add(current.toString());

                    // 重叠：取上一块末尾的内容
                    if (config.getOverlapSize() > 0) {
                        String overlapText = extractTailTokens(current.toString(), config.getOverlapSize());
                        current = new StringBuilder(overlapText);
                        if (!current.isEmpty()) {
                            current.append("\n\n");
                        }
                        current.append(trimmed);
                    } else {
                        current = new StringBuilder(trimmed);
                    }
                } else {
                    current = new StringBuilder(candidate);
                }
            }

            if (current.length() > 0) {
                result.add(current.toString());
            }
        } else {
            // 不关心段落边界，直接按字符数切分
            int maxChars = config.getMaxChunkSize() * 3;
            int overlapChars = config.getOverlapSize() * 3;

            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + maxChars, text.length());
                result.add(text.substring(start, end));
                start = end - overlapChars;
                if (start >= text.length()) {
                    break;
                }
            }
        }

        return result;
    }

    /**
     * 提取文本末尾约 tokenLimit 个 token 对应的子串
     */
    private String extractTailTokens(String text, int tokenLimit) {
        int charLimit = (int) (tokenLimit * 3.0);
        if (text.length() <= charLimit) {
            return text;
        }
        return text.substring(text.length() - charLimit);
    }

    /**
     * 构建分块对象，附加权限元数据
     */
    private DocumentChunk buildChunk(Document document, int chunkIndex,
                                     int startPage, int endPage, String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setDocumentId(document.getId());
        chunk.setDocumentKey(document.getDocumentKey());
        chunk.setChunkIndex(chunkIndex);
        chunk.setStartPage(startPage);
        chunk.setEndPage(endPage);
        chunk.setContent(content);
        chunk.setTokenCount(estimateTokenCount(content));

        // 附加权限元数据（冗余到分块级别，用于 Milvus 过滤）
        chunk.setDepartmentId(document.getDepartmentId());
        chunk.setVisibility(document.getVisibility() != null
                ? document.getVisibility().name()
                : DocumentVisibilityEnum.INTERNAL.name());
        chunk.setAllowedRoles(document.getAllowedRoles());
        chunk.setOwnerId(document.getOwnerId());

        return chunk;
    }

    /**
     * 估算文本 token 数
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return (int) Math.ceil(chineseChars / 1.5) + (int) Math.ceil(otherChars / 4.0);
    }
}
