package com.mouhin.knowledge.repository.application.executor.document;

import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 归档文档命令执行器（app 层用例）
 *
 * <p>CONC-3 / OPS-2：归档状态落库置于短事务内（单次快 IO 更新），Milvus 向量删除移出事务。 旧实现中 {@code
 * vectorStoreService.deleteByDocumentKey} 处于 {@code @Transactional} 方法体内， DB 连接自首次 SQL
 * 起一直被占用到方法返回，Milvus 抖动时会拖长事务、耗尽连接池。 现"DB 先、慢 IO 后"：状态更新成功提交即视为归档完成；向量删除失败仅记录， 检索侧以 DB
 * 状态为准，已归档文档不会再被召回（残留向量可由对账回收）。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
@Component
@Slf4j
public class DocumentArchiveCmdExe {

    private final DocumentGateway documentGateway;
    private final VectorStoreGateway vectorStoreService;
    private final TransactionTemplate transactionTemplate;

    public DocumentArchiveCmdExe(
            DocumentGateway documentGateway,
            VectorStoreGateway vectorStoreService,
            TransactionTemplate transactionTemplate) {
        this.documentGateway = documentGateway;
        this.vectorStoreService = vectorStoreService;
        this.transactionTemplate = transactionTemplate;
    }

    public void execute(String documentKey) {
        // 快 IO：归档状态落库，短事务保护。
        transactionTemplate.executeWithoutResult(
                status -> {
                    Document document =
                            documentGateway
                                    .findByDocumentKey(documentKey)
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Document not found: " + documentKey));
                    document.archive();
                    documentGateway.update(document);
                });

        // 慢 IO：从 Milvus 删除向量（归档文档不参与检索），移出事务后执行。
        try {
            vectorStoreService.deleteByDocumentKey(documentKey);
        } catch (Exception e) {
            log.warn(
                    "归档文档向量删除失败（状态已置归档，向量残留待对账回收）[documentKey={}]: {}",
                    documentKey,
                    e.getMessage());
        }

        log.info("Document {} archived", documentKey);
    }
}
