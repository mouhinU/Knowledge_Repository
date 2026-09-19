package com.mouhin.knowledge.repository.application.executor.document;

import com.mouhin.knowledge.repository.domain.gateway.DocumentChunkGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 删除文档命令执行器（app 层用例，级联清理关联数据）
 * <p>
 * CONC-3 / OPS-2：事务边界仅覆盖关系库删除（chunk 行 + document 行，均为快 IO），
 * Milvus 向量删除移出事务。旧实现把 {@code vectorStoreService.deleteByDocumentKey} 放在
 * {@code @Transactional} 方法的首行，虽在 DB 写之前调用，但事务在首次 SQL 时才从连接池取连接、
 * 直到方法返回才归还——一旦 Milvus 抖动变慢，会让 DB 连接被长事务白白占住、耗尽 HikariCP 池。
 * 现按"DB 先、慢 IO 后"：短事务提交后再删向量；向量删除失败仅记录，不会回滚已提交的 DB 删除，
 * 残留向量因缺少对应 DB 记录而不再可达（可由后续对账 / 重索引回收）。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@Component
public class DocumentDeleteCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(DocumentDeleteCmdExe.class);

    private final DocumentGateway documentGateway;
    private final DocumentChunkGateway chunkGateway;
    private final VectorStoreGateway vectorStoreService;
    private final TransactionTemplate transactionTemplate;

    public DocumentDeleteCmdExe(DocumentGateway documentGateway,
                                DocumentChunkGateway chunkGateway,
                                VectorStoreGateway vectorStoreService,
                                TransactionTemplate transactionTemplate) {
        this.documentGateway = documentGateway;
        this.chunkGateway = chunkGateway;
        this.vectorStoreService = vectorStoreService;
        this.transactionTemplate = transactionTemplate;
    }

    public void execute(String documentKey) {
        // 快 IO：关系库删除置于短事务内，保证 chunk 行与 document 行原子删除。
        transactionTemplate.executeWithoutResult(status -> {
            Document document = documentGateway.findByDocumentKey(documentKey)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentKey));
            chunkGateway.deleteByDocumentId(document.getId());
            documentGateway.deleteById(document.getId());
        });

        // 慢 IO：Milvus 向量删除移出事务，事务（及 DB 连接）已随上面块提交并释放。
        // 失败不回滚已提交的 DB 删除，仅记录告警（残留向量不可达，可由对账回收）。
        try {
            vectorStoreService.deleteByDocumentKey(documentKey);
        } catch (Exception e) {
            logger.warn("删除文档向量失败（DB 记录已删除，向量残留待对账回收）[documentKey={}]: {}",
                    documentKey, e.getMessage());
        }

        logger.info("Document {} deleted", documentKey);
    }
}
