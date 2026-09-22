package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.client.dto.CustomChunkInput;
import com.mouhin.knowledge.repository.domain.event.DocumentProcessedEvent;
import com.mouhin.knowledge.repository.domain.gateway.DocumentChunkGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.service.IndexProgressCallback;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 异步自定义分块索引用例执行器（app 层，SSE 进度回调）。
 *
 * <p>逻辑原样迁移自 {@code DocumentIngestionApplicationService.indexWithCustomChunksAsync}：在 {@link
 * CompletableFuture#runAsync} 中转换自定义分块、批量落库、带回调向量化、标记完成并发布事件。 回调由适配层创建并传入（SSE 装配保持不变），故不入对外契约。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
@Slf4j
public class IndexCustomChunksAsyncCmdExe {

    private final DocumentIngestionSupport support;
    private final ExtractionCacheHolder extractionCache;
    private final DocumentGateway documentGateway;
    private final DocumentChunkGateway chunkGateway;
    private final VectorStoreGateway vectorStoreService;
    private final ApplicationEventPublisher eventPublisher;

    public IndexCustomChunksAsyncCmdExe(
            DocumentIngestionSupport support,
            ExtractionCacheHolder extractionCache,
            DocumentGateway documentGateway,
            DocumentChunkGateway chunkGateway,
            VectorStoreGateway vectorStoreService,
            ApplicationEventPublisher eventPublisher) {
        this.support = support;
        this.extractionCache = extractionCache;
        this.documentGateway = documentGateway;
        this.chunkGateway = chunkGateway;
        this.vectorStoreService = vectorStoreService;
        this.eventPublisher = eventPublisher;
    }

    public void execute(
            String documentKey,
            List<CustomChunkInput> customChunks,
            IndexProgressCallback callback) {
        CompletableFuture.runAsync(
                () -> {
                    try {
                        if (customChunks == null || customChunks.isEmpty()) {
                            throw new IllegalArgumentException("Custom chunks must not be empty");
                        }

                        Document document =
                                documentGateway
                                        .findByDocumentKey(documentKey)
                                        .orElseThrow(
                                                () ->
                                                        new IllegalArgumentException(
                                                                "Document not found: "
                                                                        + documentKey));

                        if (document.getStatus() == DocumentStatusEnum.INDEXED) {
                            throw new IllegalStateException("Document is already indexed");
                        }

                        document.markProcessing();
                        documentGateway.update(document);

                        List<DocumentChunk> chunks =
                                support.buildCustomChunks(document, customChunks);

                        if (chunks.isEmpty()) {
                            document.markFailed("No valid chunks provided");
                            documentGateway.update(document);
                            if (callback != null) {
                                callback.onError("No valid chunks provided");
                            }
                            return;
                        }

                        chunkGateway.saveBatch(chunks);
                        vectorStoreService.storeChunks(chunks, callback);

                        ExtractionResult extraction = extractionCache.getOrReextract(document);
                        document.markIndexed(extraction.totalPages());
                        document.setFileChecksum(extraction.checksum());
                        documentGateway.update(document);

                        eventPublisher.publishEvent(
                                new DocumentProcessedEvent(
                                        document.getDocumentKey(),
                                        document.getFileName(),
                                        extraction.totalPages(),
                                        chunks.size(),
                                        document.getOwnerId(),
                                        document.getDepartmentId(),
                                        LocalDateTime.now()));

                        extractionCache.remove(documentKey);

                        log.info(
                                "Document {} indexed with custom chunks: {} chunks",
                                document.getDocumentKey(),
                                chunks.size());

                        if (callback != null) {
                            callback.onComplete();
                        }

                    } catch (Exception e) {
                        log.error(
                                "Async custom indexing failed for document {}: {}",
                                documentKey,
                                e.getMessage(),
                                e);
                        if (callback != null) {
                            callback.onError(e.getMessage());
                        }
                    }
                });
    }
}
