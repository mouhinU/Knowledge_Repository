package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.client.dto.CustomChunkInput;
import com.mouhin.knowledge.repository.domain.event.DocumentProcessedEvent;
import com.mouhin.knowledge.repository.domain.gateway.DocumentChunkGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.service.IndexProgressCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 异步重新入库用例执行器（app 层，SSE 进度回调）。
 * <p>
 * 对已入库 / 入库失败 / 已归档的文档，清理其旧向量（Milvus）、旧关系分块（{@code kb_document_chunk}）
 * 与旧配图（{@code kb_document_image} 记录 + 落盘文件），再重新提取文本、重抽配图，最后按当前分块配置
 * （或前端手动调整后的分块）重新分块 → 向量化 → 存储，全程经 {@link IndexProgressCallback} 推送进度，
 * 与首次入库（{@link IndexAsyncCmdExe} / {@link IndexCustomChunksAsyncCmdExe}）复用同一 SSE 端点。
 * </p>
 * <p>回调由适配层从 {@code IndexProgressStore} 创建并传入（SSE 传输装配保持不变），故不入对外契约。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
@Component
public class ReindexAsyncCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(ReindexAsyncCmdExe.class);

    /** 允许重新入库的起始状态：已入库、入库失败、已归档。 */
    private static final Set<DocumentStatusEnum> REINDEXABLE_STATUSES = Set.of(
            DocumentStatusEnum.INDEXED, DocumentStatusEnum.FAILED, DocumentStatusEnum.ARCHIVED);

    private final DocumentIngestionSupport support;
    private final ExtractionCacheHolder extractionCache;
    private final DocumentGateway documentGateway;
    private final DocumentChunkGateway chunkGateway;
    private final VectorStoreGateway vectorStoreService;
    private final DocumentExtractionGateway documentExtractionService;
    private final DocumentImageSupport documentImageSupport;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public ReindexAsyncCmdExe(DocumentIngestionSupport support,
                              ExtractionCacheHolder extractionCache,
                              DocumentGateway documentGateway,
                              DocumentChunkGateway chunkGateway,
                              VectorStoreGateway vectorStoreService,
                              DocumentExtractionGateway documentExtractionService,
                              DocumentImageSupport documentImageSupport,
                              ApplicationEventPublisher eventPublisher,
                              TransactionTemplate transactionTemplate) {
        this.support = support;
        this.extractionCache = extractionCache;
        this.documentGateway = documentGateway;
        this.chunkGateway = chunkGateway;
        this.vectorStoreService = vectorStoreService;
        this.documentExtractionService = documentExtractionService;
        this.documentImageSupport = documentImageSupport;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 异步重新入库。
     *
     * @param documentKey  文档标识
     * @param chunkSize    分块大小（未手动改分块时生效）
     * @param overlap      分块重叠
     * @param strategy     分块策略
     * @param customChunks 前端手动调整后的分块；非空则以其为准，忽略上面的自动分块参数
     * @param callback     SSE 进度回调
     */
    public void execute(String documentKey, int chunkSize, int overlap, String strategy,
                        List<CustomChunkInput> customChunks, IndexProgressCallback callback) {
        boolean useCustom = customChunks != null && !customChunks.isEmpty();
        CompletableFuture.runAsync(() -> {
            try {
                Document document = prepareForReindex(documentKey);
                ExtractionResult result = reextract(document);
                documentImageSupport.extractAndPersist(document, Path.of(document.getStoragePath()));

                if (useCustom) {
                    processCustomChunks(document, result, customChunks, callback);
                } else {
                    ChunkingConfig config = support.buildConfig(chunkSize, overlap, support.resolveStrategy(strategy));
                    document.setChunkingConfig(config);
                    support.processDocument(document, result, callback);
                }
            } catch (Exception e) {
                logger.error("Async reindex failed for document {}: {}", documentKey, e.getMessage(), e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }

    /**
     * 前置清理：校验状态 → 删除旧向量 → 短事务删旧分块并重置状态 → 删除旧配图（记录 + 文件）→ 失效提取缓存。
     */
    private Document prepareForReindex(String documentKey) {
        Document document = documentGateway.findByDocumentKey(documentKey)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentKey));

        if (!REINDEXABLE_STATUSES.contains(document.getStatus())) {
            throw new IllegalStateException("文档当前状态不支持重新入库: " + document.getStatus());
        }

        Path storagePath = Path.of(document.getStoragePath());
        if (!Files.exists(storagePath)) {
            throw new IllegalStateException("Stored file not found at: " + document.getStoragePath());
        }

        // 快 IO：Milvus 向量删除移出事务，避免 DB 连接被向量库抖动拖住。
        vectorStoreService.deleteByDocumentKey(documentKey);

        // 快 IO：关系库清理 + 状态重置置于短事务，保证 chunk 行删除与状态回退原子发生。
        transactionTemplate.executeWithoutResult(status -> {
            chunkGateway.deleteByDocumentId(document.getId());
            document.setStatus(DocumentStatusEnum.UPLOADED);
            document.setErrorMessage(null);
            documentGateway.update(document);
            // updateById 在 NOT_NULL 策略下不会把 error_message 写回 null，须走专用清列通道（DATA-3）
            documentGateway.clearErrorMessage(document.getId());
        });

        // 删图并重抽：清除旧配图记录与落盘文件（生成新 assetKey 会使旧人工配图绑定失效，前端已警示）。
        documentImageSupport.deleteImages(document);

        // 强制重新提取，避免命中上一次入库遗留的缓存结果。
        extractionCache.remove(documentKey);
        return document;
    }

    /** 慢 IO（文件重解析）：在事务之外重新提取文本并回填缓存。 */
    private ExtractionResult reextract(Document document) {
        Path storagePath = Path.of(document.getStoragePath());
        try {
            ExtractionResult result = documentExtractionService.extractText(
                    storagePath, Files.size(storagePath), document.getFileName());
            extractionCache.put(document.getDocumentKey(), result);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to re-extract text: " + e.getMessage(), e);
        }
    }

    /**
     * 自定义分块入库收尾（重新入库手动调整分块分支）：分块落库 → 带回调向量化 → 标记完成 → 发布事件。
     * 与 {@link IndexCustomChunksAsyncCmdExe} 对齐，差异仅在于前置清理已在此执行器完成。
     */
    private void processCustomChunks(Document document, ExtractionResult result,
                                     List<CustomChunkInput> customChunks, IndexProgressCallback callback) {
        document.markProcessing();
        documentGateway.update(document);

        List<DocumentChunk> chunks = support.buildCustomChunks(document, customChunks);
        if (chunks.isEmpty()) {
            document.markFailed("No valid chunks provided");
            documentGateway.update(document);
            if (callback != null) {
                callback.onError("No valid chunks provided");
            }
            return;
        }

        try {
            chunkGateway.saveBatch(chunks);
            vectorStoreService.storeChunks(chunks, callback);

            document.markIndexed(result.totalPages());
            document.setFileChecksum(result.checksum());
            documentGateway.update(document);

            eventPublisher.publishEvent(new DocumentProcessedEvent(
                    document.getDocumentKey(), document.getFileName(),
                    result.totalPages(), chunks.size(),
                    document.getOwnerId(), document.getDepartmentId(), LocalDateTime.now()));

            logger.info("Document {} reindexed with custom chunks: {} chunks",
                    document.getDocumentKey(), chunks.size());

            if (callback != null) {
                callback.onComplete();
            }
        } catch (Exception e) {
            logger.error("Failed to store reindex chunks for {}: {}", document.getDocumentKey(), e.getMessage(), e);
            document.markFailed(e.getMessage());
            documentGateway.update(document);
            if (callback != null) {
                callback.onError(e.getMessage());
            }
        }
    }
}
