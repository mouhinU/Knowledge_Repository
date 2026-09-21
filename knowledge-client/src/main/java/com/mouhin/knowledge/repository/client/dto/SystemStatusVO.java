package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 系统状态概览视图对象（adapter 响应体）
 *
 * <p>结构与既有 /api/admin/system/status 的 JSON 完全一致：embedding / milvus / knowledgeBase 三段，字段顺序亦保持一致。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Getter
@Setter
public class SystemStatusVO {

    private EmbeddingInfo embedding;
    private MilvusInfo milvus;
    private KnowledgeBaseStats knowledgeBase;

    /** Embedding 模型信息 */
    @Getter
    @Setter
    public static class EmbeddingInfo {
        private String provider;
        private String modelClass;
    }

    /** Milvus 连接信息 */
    @Getter
    @Setter
    public static class MilvusInfo {
        private String host;
        private int port;
        private String collection;
        private String status;
    }

    /** 知识库统计 */
    @Getter
    @Setter
    public static class KnowledgeBaseStats {
        private long indexedDocuments;
        private long processingDocuments;
        private long failedDocuments;
        private long totalDocuments;
    }
}
