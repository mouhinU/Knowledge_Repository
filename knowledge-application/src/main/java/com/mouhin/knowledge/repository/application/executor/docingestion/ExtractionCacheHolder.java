package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文本提取结果缓存（app 层，跨用例共享状态）
 *
 * <p>收敛原 {@code DocumentIngestionApplicationService} 中的 {@code extractionCache} 实例字段： 首次解析预览时由
 * {@link PreviewFromDocumentQryExe} 通过 {@link #getOrReextract} 触发提取并放入缓存， 后续预览直接读缓存；reindex 前通过
 * {@link #remove} 强制失效。以单例 Bean 承载，保证各执行器操作同一份缓存。 缓存未命中时从存储文件重新提取。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
@Slf4j
public class ExtractionCacheHolder {

    private final Map<String, ExtractionResult> cache = new ConcurrentHashMap<>();

    private final DocumentExtractionGateway documentExtractionService;

    public ExtractionCacheHolder(DocumentExtractionGateway documentExtractionService) {
        this.documentExtractionService = documentExtractionService;
    }

    public void put(String documentKey, ExtractionResult result) {
        cache.put(documentKey, result);
    }

    public void remove(String documentKey) {
        cache.remove(documentKey);
    }

    /** 获取缓存的提取结果，若缓存未命中则从存储文件重新提取。 */
    public ExtractionResult getOrReextract(Document document) {
        ExtractionResult cached = cache.get(document.getDocumentKey());
        if (cached != null) {
            return cached;
        }

        Path storedPath = Path.of(document.getStoragePath());
        if (!Files.exists(storedPath)) {
            throw new IllegalStateException(
                    "Stored file not found at: " + document.getStoragePath());
        }

        try {
            log.info(
                    "Re-extracting text for document {}: {}",
                    document.getDocumentKey(),
                    document.getFileName());
            ExtractionResult result =
                    documentExtractionService.extractText(
                            storedPath, Files.size(storedPath), document.getFileName());
            cache.put(document.getDocumentKey(), result);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to re-extract text: " + e.getMessage(), e);
        }
    }
}
