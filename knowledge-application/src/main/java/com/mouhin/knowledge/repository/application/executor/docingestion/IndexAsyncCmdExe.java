package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.service.IndexProgressCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 异步索引文档用例执行器（app 层，SSE 进度回调）。
 * <p>
 * 逻辑原样迁移自 {@code DocumentIngestionApplicationService.indexDocumentAsync}：在
 * {@link CompletableFuture#runAsync} 中执行分块入库，进度 / 错误经 {@link IndexProgressCallback} 推送。
 * 回调由适配层从 SSE 存储组件创建并传入（SSE 传输装配保持不变），故不入对外契约。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class IndexAsyncCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(IndexAsyncCmdExe.class);

    private final DocumentIngestionSupport support;
    private final ExtractionCacheHolder extractionCache;
    private final DocumentGateway documentGateway;

    public IndexAsyncCmdExe(DocumentIngestionSupport support,
                            ExtractionCacheHolder extractionCache,
                            DocumentGateway documentGateway) {
        this.support = support;
        this.extractionCache = extractionCache;
        this.documentGateway = documentGateway;
    }

    public void execute(String documentKey, int chunkSize, int overlap,
                        String strategy, IndexProgressCallback callback) {
        CompletableFuture.runAsync(() -> {
            try {
                Document document = documentGateway.findByDocumentKey(documentKey)
                        .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentKey));

                if (document.getStatus() == DocumentStatusEnum.INDEXED) {
                    throw new IllegalStateException("Document is already indexed");
                }

                ChunkingConfig config = support.buildConfig(chunkSize, overlap, support.resolveStrategy(strategy));
                document.setChunkingConfig(config);

                ExtractionResult extraction = extractionCache.getOrReextract(document);
                support.processDocument(document, extraction, callback);

                extractionCache.remove(documentKey);
            } catch (Exception e) {
                logger.error("Async indexing failed for document {}: {}", documentKey, e.getMessage(), e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
}
