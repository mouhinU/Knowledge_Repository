package com.mouhin.knowledge.repository.application.executor.document;

import com.mouhin.knowledge.repository.domain.gateway.DocumentChunkGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 删除文档命令执行器（app 层用例，事务边界，级联清理关联数据）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class DocumentDeleteCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(DocumentDeleteCmdExe.class);

    private final DocumentGateway documentGateway;
    private final DocumentChunkGateway chunkGateway;
    private final VectorStoreGateway vectorStoreService;

    public DocumentDeleteCmdExe(DocumentGateway documentGateway,
                                DocumentChunkGateway chunkGateway,
                                VectorStoreGateway vectorStoreService) {
        this.documentGateway = documentGateway;
        this.chunkGateway = chunkGateway;
        this.vectorStoreService = vectorStoreService;
    }

    @Transactional
    public void execute(String documentKey) {
        Document document = documentGateway.findByDocumentKey(documentKey)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentKey));

        // 1. 删除 Milvus 向量
        vectorStoreService.deleteByDocumentKey(documentKey);

        // 2. 删除分块记录
        chunkGateway.deleteByDocumentId(document.getId());

        // 3. 删除文档记录
        documentGateway.deleteById(document.getId());

        logger.info("Document {} deleted", documentKey);
    }
}
