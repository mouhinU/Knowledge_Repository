package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.repository.DocumentRepository;
import com.mouhin.knowledge.repository.infrastructure.milvus.MilvusVectorStoreService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统配置应用服务
 * <p>
 * 提供系统状态查询：Embedding 模型信息、Milvus 连接状态、知识库统计。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Service
public class SystemConfigApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigApplicationService.class);

    private final EmbeddingModel embeddingModel;
    private final MilvusVectorStoreService vectorStoreService;
    private final DocumentRepository documentRepository;

    @Value("${knowledge.embedding.provider:unknown}")
    private String embeddingProvider;

    @Value("${knowledge.milvus.host:localhost}")
    private String milvusHost;

    @Value("${knowledge.milvus.port:19530}")
    private int milvusPort;

    @Value("${knowledge.milvus.collection-name:knowledge_chunks}")
    private String milvusCollection;

    public SystemConfigApplicationService(EmbeddingModel embeddingModel,
                                          MilvusVectorStoreService vectorStoreService,
                                          DocumentRepository documentRepository) {
        this.embeddingModel = embeddingModel;
        this.vectorStoreService = vectorStoreService;
        this.documentRepository = documentRepository;
    }

    /**
     * 获取系统状态概览
     */
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        // Embedding 模型信息
        Map<String, Object> embeddingInfo = new LinkedHashMap<>();
        embeddingInfo.put("provider", embeddingProvider);
        embeddingInfo.put("modelClass", embeddingModel.getClass().getSimpleName());
        status.put("embedding", embeddingInfo);

        // Milvus 连接信息
        Map<String, Object> milvusInfo = new LinkedHashMap<>();
        milvusInfo.put("host", milvusHost);
        milvusInfo.put("port", milvusPort);
        milvusInfo.put("collection", milvusCollection);
        milvusInfo.put("status", checkMilvusConnection());
        status.put("milvus", milvusInfo);

        // 知识库统计
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("indexedDocuments", documentRepository.countByStatus(DocumentStatusEnum.INDEXED));
        stats.put("processingDocuments", documentRepository.countByStatus(DocumentStatusEnum.PROCESSING));
        stats.put("failedDocuments", documentRepository.countByStatus(DocumentStatusEnum.FAILED));
        stats.put("totalDocuments",
                (long) stats.get("indexedDocuments") + (long) stats.get("processingDocuments")
                        + (long) stats.get("failedDocuments"));
        status.put("knowledgeBase", stats);

        return status;
    }

    /**
     * 检查 Milvus 连接状态
     */
    private String checkMilvusConnection() {
        try {
            // 尝试一个轻量操作来验证连接
            vectorStoreService.search("health_check", 1, 0.0, null);
            return "connected";
        } catch (Exception e) {
            logger.warn("Milvus connection check failed: {}", e.getMessage());
            return "disconnected: " + e.getMessage();
        }
    }
}
