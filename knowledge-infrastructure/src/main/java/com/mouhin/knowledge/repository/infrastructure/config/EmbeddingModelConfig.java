package com.mouhin.knowledge.repository.infrastructure.config;

import com.mouhin.knowledge.repository.infrastructure.llm.LlmResilience;
import com.mouhin.knowledge.repository.infrastructure.llm.ResilientEmbeddingModel;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Embedding 角色模型装配（Phase R1 三段式）。
 *
 * <p>统一以 OpenAI 兼容协议构建唯一 {@link EmbeddingModel}（本地 BGE-M3 或 DashScope 均由 base-url 决定），并注入 embedding
 * 角色的独立 {@link HttpClientBuilder}：向量调用短平快，connect/read/call 给到秒级快速失败，与 chat 长对话隔离，
 * 避免慢对话占满连接导致向量化被饿死。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Configuration
@Slf4j
public class EmbeddingModelConfig {

    /** Embedding 模型，注入 embedding 角色独立 HTTP 客户端，并套 {@code embedding} 角色熔断/重试。 */
    @Bean
    public EmbeddingModel embeddingModel(
            LlmEmbeddingProperties embedding,
            LlmResilience resilience,
            @Qualifier("embeddingHttpClientBuilder") HttpClientBuilder httpClientBuilder) {
        log.info(
                "Initializing embedding model: {} at {} (provider={})",
                embedding.getModelName(),
                embedding.getBaseUrl(),
                embedding.getProvider());
        EmbeddingModel raw =
                OpenAiEmbeddingModel.builder()
                        .baseUrl(embedding.getBaseUrl())
                        .apiKey(embedding.getApiKey())
                        .modelName(embedding.getModelName())
                        .httpClientBuilder(httpClientBuilder)
                        .timeout(Duration.ofSeconds(Math.max(embedding.getCallTimeoutSeconds(), 5)))
                        .build();
        return new ResilientEmbeddingModel(raw, resilience);
    }
}
