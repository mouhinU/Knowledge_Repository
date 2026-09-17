package com.mouhin.knowledge.repository.application.executor.document;

import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 归档文档命令执行器（app 层用例，事务边界）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class DocumentArchiveCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(DocumentArchiveCmdExe.class);

    private final DocumentGateway documentGateway;
    private final VectorStoreGateway vectorStoreService;

    public DocumentArchiveCmdExe(DocumentGateway documentGateway,
                                 VectorStoreGateway vectorStoreService) {
        this.documentGateway = documentGateway;
        this.vectorStoreService = vectorStoreService;
    }

    @Transactional
    public void execute(String documentKey) {
        Document document = documentGateway.findByDocumentKey(documentKey)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentKey));
        document.archive();
        documentGateway.update(document);

        // 从 Milvus 删除向量（归档文档不参与检索）
        vectorStoreService.deleteByDocumentKey(documentKey);

        logger.info("Document {} archived", documentKey);
    }
}
