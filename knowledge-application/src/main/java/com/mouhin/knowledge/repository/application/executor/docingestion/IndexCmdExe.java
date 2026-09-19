package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.application.converter.DocumentConverter;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import org.springframework.stereotype.Component;

/**
 * 确认入库用例执行器（app 层，同步：分块 → 向量化 → 存储）。
 * <p>
 * 逻辑原样迁移自 {@code DocumentIngestionApplicationService.indexDocument}。当前线上走异步 SSE 入口，
 * 同步入口作为完整用例保留。
 * </p>
 *
 * <p>CONC-3 / OPS-2：本方法不再标注 {@code @Transactional}。实际工作委托给
 * {@link DocumentIngestionSupport#processDocument} —— 该方法逐步自行落库并在异常时内部吞掉、
 * 标记 FAILED 后正常返回，方法级事务既提供不了回滚保证，又会在耗时的向量化 IO 期间持续占用
 * HikariCP 连接。去掉事务后与异步入口 {@code IndexAsyncCmdExe}（同样无事务）行为一致。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class IndexCmdExe {

    private final DocumentIngestionSupport support;
    private final ExtractionCacheHolder extractionCache;
    private final DocumentGateway documentGateway;

    public IndexCmdExe(DocumentIngestionSupport support,
                       ExtractionCacheHolder extractionCache,
                       DocumentGateway documentGateway) {
        this.support = support;
        this.extractionCache = extractionCache;
        this.documentGateway = documentGateway;
    }

    public DocumentVO execute(String documentKey, int chunkSize, int overlap, String strategy) {
        Document document = documentGateway.findByDocumentKey(documentKey)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentKey));

        if (document.getStatus() == DocumentStatusEnum.INDEXED) {
            throw new IllegalStateException("Document is already indexed. Use reindex to re-process.");
        }

        ChunkingConfig config = support.buildConfig(chunkSize, overlap, support.resolveStrategy(strategy));
        document.setChunkingConfig(config);

        ExtractionResult extraction = extractionCache.getOrReextract(document);
        support.processDocument(document, extraction);

        extractionCache.remove(documentKey);

        return DocumentConverter.toVO(document);
    }
}
