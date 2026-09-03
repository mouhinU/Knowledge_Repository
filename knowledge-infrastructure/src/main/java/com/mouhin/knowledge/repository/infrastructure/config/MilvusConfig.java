package com.mouhin.knowledge.repository.infrastructure.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 向量存储配置
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Configuration
public class MilvusConfig {

    private static final Logger logger = LoggerFactory.getLogger(MilvusConfig.class);

    @Value("${knowledge.milvus.host:localhost}")
    private String host;

    @Value("${knowledge.milvus.port:19530}")
    private int port;

    @Value("${knowledge.milvus.collection-name:knowledge_chunks}")
    private String collectionName;

    @Value("${knowledge.milvus.dimension:1536}")
    private int dimension;

    @Value("${knowledge.milvus.database-name:default}")
    private String databaseName;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        logger.info("Initializing Milvus embedding store: {}:{}, collection={}", host, port, collectionName);

        return MilvusEmbeddingStore.builder()
                .host(host)
                .port(port)
                .collectionName(collectionName)
                .dimension(dimension)
                .databaseName(databaseName)
                .build();
    }
}
