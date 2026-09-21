package com.mouhin.knowledge.repository.application.executor.system;

import com.mouhin.knowledge.repository.client.dto.SystemStatusVO;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 系统状态查询执行器（app 层用例）
 *
 * <p>组装逻辑与原 {@code SystemConfigApplicationService.getSystemStatus()} 一致： Embedding 信息、Milvus
 * 连接探测、知识库统计。返回值结构与既有 JSON 完全对应。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class SystemStatusQryExe {

    private static final Logger logger = LoggerFactory.getLogger(SystemStatusQryExe.class);

    private final EmbeddingModel embeddingModel;
    private final VectorStoreGateway vectorStoreGateway;
    private final DocumentGateway documentGateway;

    @Value("${knowledge.embedding.provider:unknown}")
    private String embeddingProvider;

    @Value("${knowledge.milvus.host:localhost}")
    private String milvusHost;

    @Value("${knowledge.milvus.port:19530}")
    private int milvusPort;

    @Value("${knowledge.milvus.collection-name:knowledge_chunks}")
    private String milvusCollection;

    public SystemStatusQryExe(
            EmbeddingModel embeddingModel,
            VectorStoreGateway vectorStoreGateway,
            DocumentGateway documentGateway) {
        this.embeddingModel = embeddingModel;
        this.vectorStoreGateway = vectorStoreGateway;
        this.documentGateway = documentGateway;
    }

    public SystemStatusVO execute() {
        SystemStatusVO status = new SystemStatusVO();

        SystemStatusVO.EmbeddingInfo embeddingInfo = new SystemStatusVO.EmbeddingInfo();
        embeddingInfo.setProvider(embeddingProvider);
        embeddingInfo.setModelClass(embeddingModel.getClass().getSimpleName());
        status.setEmbedding(embeddingInfo);

        SystemStatusVO.MilvusInfo milvusInfo = new SystemStatusVO.MilvusInfo();
        milvusInfo.setHost(milvusHost);
        milvusInfo.setPort(milvusPort);
        milvusInfo.setCollection(milvusCollection);
        milvusInfo.setStatus(checkMilvusConnection());
        status.setMilvus(milvusInfo);

        SystemStatusVO.KnowledgeBaseStats stats = new SystemStatusVO.KnowledgeBaseStats();
        long indexed = documentGateway.countByStatus(DocumentStatusEnum.INDEXED);
        long processing = documentGateway.countByStatus(DocumentStatusEnum.PROCESSING);
        long failed = documentGateway.countByStatus(DocumentStatusEnum.FAILED);
        stats.setIndexedDocuments(indexed);
        stats.setProcessingDocuments(processing);
        stats.setFailedDocuments(failed);
        stats.setTotalDocuments(indexed + processing + failed);
        status.setKnowledgeBase(stats);

        return status;
    }

    /** 检查 Milvus 连接状态（轻量向量检索探测） */
    private String checkMilvusConnection() {
        try {
            vectorStoreGateway.search("health_check", 1, 0.0, null);
            return "connected";
        } catch (Exception e) {
            logger.warn("Milvus connection check failed: {}", e.getMessage());
            return "disconnected: " + e.getMessage();
        }
    }
}
