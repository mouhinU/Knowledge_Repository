package com.mouhin.knowledge.repository.infrastructure.config;

import com.mouhin.knowledge.repository.infrastructure.llm.SimpleHttpScoringModel;
import dev.langchain4j.model.scoring.ScoringModel;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reranker 角色模型装配。
 *
 * <p>当 {@code knowledge.llm.reranker.enabled=true} 时构建 {@link ScoringModel} Bean，
 * 用于检索后重排（cross-encoder 对 query-document 对精准打分）。
 *
 * <p>默认关闭；启用后需配置 base-url / model-name 指向可用的 reranker 服务（如 bge-reranker-v2-m3）。
 *
 * @author mouhinU
 * @date 2026-09-25
 */
@Configuration
@Slf4j
public class RerankerModelConfig {

    @Bean
    @ConditionalOnProperty(
            prefix = "knowledge.llm.reranker",
            name = "enabled",
            havingValue = "true")
    public ScoringModel scoringModel(LlmRerankerProperties reranker) {
        log.info(
                "Initializing reranker model: {} at {} (provider={})",
                reranker.getModelName(),
                reranker.getBaseUrl(),
                reranker.getProvider());
        return new SimpleHttpScoringModel(
                reranker.getBaseUrl(),
                reranker.getApiKey(),
                reranker.getModelName(),
                Duration.ofSeconds(Math.max(reranker.getCallTimeoutSeconds(), 5)));
    }
}
