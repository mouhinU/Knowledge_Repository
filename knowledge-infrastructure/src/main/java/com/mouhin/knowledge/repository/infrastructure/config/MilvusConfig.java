package com.mouhin.knowledge.repository.infrastructure.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Milvus 向量存储配置
 *
 * <p>OPS-1：MilvusEmbeddingStore 构建时会立即连接 Milvus 并创建/校验 collection，若在容器编排里 应用先于 Milvus 就绪启动，会抛
 * DEADLINE_EXCEEDED 导致上下文启动失败（需整容器重启才能自愈）。 现将该 bean 标注 {@link Lazy}，把建连推迟到首次检索/写入时，避免启动期硬失败；同时加入有界
 * 退避重试，容忍 Milvus 冷启动的短暂不可用窗口——重试仍失败才向上抛错，使故障暴露在一次请求而非 静默。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
@Configuration
@Slf4j
public class MilvusConfig {

    /** 建连失败最大重试次数。 */
    private static final int MAX_CONNECT_ATTEMPTS = 5;

    /** 重试基础退避（毫秒），第 n 次等待 n * BASE。 */
    private static final long RETRY_BACKOFF_BASE_MS = 2000L;

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

    @Lazy
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        log.info(
                "Initializing Milvus embedding store (lazy): {}:{}, collection={}",
                host,
                port,
                collectionName);
        return buildWithRetry();
    }

    /**
     * 带线性退避重试的构建，容忍 Milvus 冷启动的瞬时不可用。
     *
     * @return 已建立的 {@link MilvusEmbeddingStore}
     * @throws IllegalStateException 超过最大尝试仍失败时抛出，保留最后一次根因
     */
    private EmbeddingStore<TextSegment> buildWithRetry() {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_CONNECT_ATTEMPTS; attempt++) {
            try {
                MilvusEmbeddingStore store =
                        MilvusEmbeddingStore.builder()
                                .host(host)
                                .port(port)
                                .collectionName(collectionName)
                                .dimension(dimension)
                                .databaseName(databaseName)
                                .build();
                if (attempt > 1) {
                    log.info("Milvus embedding store 建连成功（第 {} 次尝试）", attempt);
                }
                return store;
            } catch (RuntimeException e) {
                lastError = e;
                log.warn(
                        "Milvus embedding store 建连失败（第 {}/{} 次）：{}",
                        attempt,
                        MAX_CONNECT_ATTEMPTS,
                        e.getMessage());
                if (attempt < MAX_CONNECT_ATTEMPTS) {
                    sleepBackoff(attempt);
                }
            }
        }
        throw new IllegalStateException(
                "Milvus embedding store 初始化失败，已重试 " + MAX_CONNECT_ATTEMPTS + " 次", lastError);
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(RETRY_BACKOFF_BASE_MS * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Milvus embedding store 初始化重试被中断", ie);
        }
    }
}
