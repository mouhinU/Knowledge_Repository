package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.application.converter.DocumentConverter;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.domain.gateway.DocumentChunkGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 重新入库已有文档用例执行器（app 层，清理旧向量 / 分块后重新提取、分块、向量化）。
 * <p>
 * 逻辑原样迁移自 {@code DocumentIngestionApplicationService.reindex}。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ReindexCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(ReindexCmdExe.class);

    private final DocumentIngestionSupport support;
    private final DocumentGateway documentGateway;
    private final DocumentChunkGateway chunkGateway;
    private final VectorStoreGateway vectorStoreService;
    private final DocumentExtractionGateway documentExtractionService;
    private final TransactionTemplate transactionTemplate;

    public ReindexCmdExe(DocumentIngestionSupport support,
                         DocumentGateway documentGateway,
                         DocumentChunkGateway chunkGateway,
                         VectorStoreGateway vectorStoreService,
                         DocumentExtractionGateway documentExtractionService,
                         TransactionTemplate transactionTemplate) {
        this.support = support;
        this.documentGateway = documentGateway;
        this.chunkGateway = chunkGateway;
        this.vectorStoreService = vectorStoreService;
        this.documentExtractionService = documentExtractionService;
        this.transactionTemplate = transactionTemplate;
    }

    public DocumentVO execute(String documentKey) {
        Document document = documentGateway.findByDocumentKey(documentKey)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentKey));

        Path storagePath = Path.of(document.getStoragePath());
        if (!Files.exists(storagePath)) {
            throw new IllegalStateException(
                    "Stored file not found at: " + document.getStoragePath());
        }

        // 快 IO：Milvus 向量删除移出事务，先于短事务执行，避免 DB 连接被向量库抖动拖住。
        vectorStoreService.deleteByDocumentKey(documentKey);

        // 快 IO：关系库清理 + 状态重置置于短事务，保证 chunk 行删除与文档状态回退原子发生。
        transactionTemplate.executeWithoutResult(status -> {
            chunkGateway.deleteByDocumentId(document.getId());
            document.setStatus(DocumentStatusEnum.UPLOADED);
            document.setErrorMessage(null);
            documentGateway.update(document);
            // updateById 在 NOT_NULL 策略下不会把 error_message 写回 null，须走专用清列通道（DATA-3）
            documentGateway.clearErrorMessage(document.getId());
        });

        logger.info("Reindexing document {}: {}", documentKey, document.getFileName());

        try {
            // 慢 IO（文件重解析 + 分块→向量化→存储）在事务之外执行，全程不占用 DB 连接。
            ExtractionResult result =
                    documentExtractionService.extractText(
                            storagePath, Files.size(storagePath), document.getFileName());

            support.processDocument(document, result);
            return DocumentConverter.toVO(document);

        } catch (IOException e) {
            throw new IllegalStateException("Failed to re-extract text: " + e.getMessage(), e);
        }
    }
}
