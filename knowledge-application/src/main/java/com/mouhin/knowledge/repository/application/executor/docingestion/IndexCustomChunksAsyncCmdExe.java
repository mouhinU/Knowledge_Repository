package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.client.dto.CustomChunkInput;
import com.mouhin.knowledge.repository.domain.event.DocumentProcessedEvent;
import com.mouhin.knowledge.repository.domain.gateway.DocumentChunkGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.service.IndexProgressCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

/**
 * 异步自定义分块索引用例执行器（app 层，SSE 进度回调）。
 * <p>
 * 逻辑原样迁移自 {@code DocumentIngestionApplicationService.indexWithCustomChunksAsync}：在
 * {@link CompletableFuture#runAsync} 中转换自定义分块、批量落库、带回调向量化、标记完成并发布事件。
 * 回调由适配层创建并传入（SSE 装配保持不变），故不入对外契约。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class IndexCustomChunksAsyncCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(IndexCustomChunksAsyncCmdExe.class);

    private final DocumentIngestionSupport support;
    private final ExtractionCacheHolder extractionCache;
    private final DocumentGateway documentGateway;
    private final DocumentChunkGateway chunkGateway;
    private final VectorStoreGateway vectorStoreService;
    private final ApplicationEventPublisher eventPublisher;

    public IndexCustomChunksAsyncCmdExe(DocumentIngestionSupport support,
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

    public void execute(String documentKey, List<CustomChunkInput> customChunks,
                        IndexProgressCallback callback) {
        CompletableFuture.runAsync(() -> {
            try {
                if (customChunks == null || customChunks.isEmpty()) {
                    throw new IllegalArgumentException("Custom chunks must not be empty");
                }

                Document document = documentGateway.findByDocumentKey(documentKey)
                        .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentKey));

                if (document.getStatus() == DocumentStatusEnum.INDEXED) {
                    throw new IllegalStateException("Document is already indexed");
                }

                document.markProcessing();
                documentGateway.update(document);

                List<DocumentChunk> chunks = buildChunks(document, customChunks);

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

                eventPublisher.publishEvent(new DocumentProcessedEvent(
                        document.getDocumentKey(), document.getFileName(),
                        extraction.totalPages(), chunks.size(),
                        document.getOwnerId(), document.getDepartmentId(), LocalDateTime.now()));

                extractionCache.remove(documentKey);

                logger.info("Document {} indexed with custom chunks: {} chunks",
                        document.getDocumentKey(), chunks.size());

                if (callback != null) {
                    callback.onComplete();
                }

            } catch (Exception e) {
                logger.error("Async custom indexing failed for document {}: {}",
                        documentKey, e.getMessage(), e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }

    private List<DocumentChunk> buildChunks(Document document, List<CustomChunkInput> customChunks) {
        List<DocumentChunk> chunks = new ArrayList<>(customChunks.size());
        int index = 0;
        for (CustomChunkInput input : customChunks) {
            if (input.content() == null || input.content().isBlank()) {
                continue;
            }
            DocumentChunk chunk = new DocumentChunk();
            chunk.setChunkKey(UUID.randomUUID().toString());
            chunk.setDocumentId(document.getId());
            chunk.setDocumentKey(document.getDocumentKey());
            chunk.setChunkIndex(index++);
            chunk.setStartPage(input.startPage());
            chunk.setEndPage(input.endPage());
            chunk.setContent(input.content());
            chunk.setTokenCount(chunk.estimateTokenCount(input.content()));
            chunk.setDepartmentId(document.getDepartmentId());
            chunk.setVisibility(document.getVisibility() != null
                    ? document.getVisibility().name()
                    : DocumentVisibilityEnum.INTERNAL.name());
            chunk.setAllowedRoles(document.getAllowedRoles());
            chunk.setOwnerId(document.getOwnerId());
            chunk.setDocumentName(document.getFileName());
            chunk.setFileType(document.getFileType());
            chunk.setTags(document.getTags());
            chunks.add(chunk);
        }
        return chunks;
    }
}
