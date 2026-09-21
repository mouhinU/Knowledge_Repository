package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.application.converter.DocumentConverter;
import com.mouhin.knowledge.repository.client.dto.CustomChunkInput;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.domain.event.DocumentProcessedEvent;
import com.mouhin.knowledge.repository.domain.gateway.DocumentChunkGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 使用用户自定义分块入库用例执行器（app 层，同步）。
 *
 * <p>逻辑原样迁移自 {@code DocumentIngestionApplicationService.indexWithCustomChunks}，含 catch 内
 * 标记失败并返回文档的语义。当前线上走异步 SSE 入口，同步入口作为完整用例保留。
 *
 * <p>CONC-3 / OPS-2：不再标注 {@code @Transactional}。用例中的 {@code vectorStoreService.storeChunks} 是耗时的
 * Ollama 向量化 + Milvus 写入，若被方法级事务包裹， 会在整个 embedding 期间持续占用 HikariCP 连接。而本用例的 try/catch 本就逐步落库、异常时标记
 * FAILED 并正常返回（事务无法回滚该吞掉的异常），去掉事务与异步索引入口（无事务）保持一致。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
@Slf4j
public class IndexCustomChunksCmdExe {

    private final DocumentIngestionSupport support;
    private final ExtractionCacheHolder extractionCache;
    private final DocumentGateway documentGateway;
    private final DocumentChunkGateway chunkGateway;
    private final VectorStoreGateway vectorStoreService;
    private final ApplicationEventPublisher eventPublisher;

    public IndexCustomChunksCmdExe(
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

    public DocumentVO execute(String documentKey, List<CustomChunkInput> customChunks) {
        if (customChunks == null || customChunks.isEmpty()) {
            throw new IllegalArgumentException("Custom chunks must not be empty");
        }

        Document document =
                documentGateway
                        .findByDocumentKey(documentKey)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Document not found: " + documentKey));

        if (document.getStatus() == DocumentStatusEnum.INDEXED) {
            throw new IllegalStateException(
                    "Document is already indexed. Use reindex to re-process.");
        }

        try {
            document.markProcessing();
            documentGateway.update(document);

            List<DocumentChunk> chunks = buildChunks(document, customChunks);

            if (chunks.isEmpty()) {
                document.markFailed("No valid chunks provided");
                documentGateway.update(document);
                return DocumentConverter.toVO(document);
            }

            chunkGateway.saveBatch(chunks);
            vectorStoreService.storeChunks(chunks);

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

            log.info(
                    "Document {} indexed with custom chunks: {} chunks",
                    document.getDocumentKey(),
                    chunks.size());

            extractionCache.remove(documentKey);

            return DocumentConverter.toVO(document);

        } catch (Exception e) {
            log.error(
                    "Failed to index document {} with custom chunks: {}",
                    documentKey,
                    e.getMessage(),
                    e);
            document.markFailed(e.getMessage());
            documentGateway.update(document);
            return DocumentConverter.toVO(document);
        }
    }

    private List<DocumentChunk> buildChunks(
            Document document, List<CustomChunkInput> customChunks) {
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
            chunk.setVisibility(
                    document.getVisibility() != null
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
