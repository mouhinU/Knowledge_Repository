package com.mouhin.knowledge.repository.infrastructure.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Embedding 模型配置
 *
 * <p>通过 knowledge.embedding.provider 配置项切换 Embedding 模型提供商：
 *
 * <ul>
 *   <li>dashscope — 阿里云通义 DashScope（OpenAI 兼容端点）
 *   <li>ollama — 本地 Ollama（OpenAI 兼容接口）
 * </ul>
 *
 * 两种提供商均通过 OpenAI 兼容协议接入，统一使用 OpenAiEmbeddingModel。
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Configuration
@Slf4j
public class EmbeddingModelConfig {

    /** DashScope Embedding 模型（通过 OpenAI 兼容端点） */
    @Bean
    @ConditionalOnProperty(name = "knowledge.embedding.provider", havingValue = "dashscope")
    public EmbeddingModel dashScopeEmbeddingModel(
            @Value("${knowledge.embedding.dashscope.api-key}") String apiKey,
            @Value("${knowledge.embedding.dashscope.model-name:text-embedding-v3}")
                    String modelName) {
        String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        log.info("Initializing DashScope embedding model: {} at {}", modelName, baseUrl);
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    /** Ollama Embedding 模型（通过 OpenAI 兼容接口） */
    @Bean
    @ConditionalOnProperty(name = "knowledge.embedding.provider", havingValue = "ollama")
    public EmbeddingModel ollamaEmbeddingModel(
            @Value("${knowledge.embedding.ollama.base-url:http://localhost:11434/v1}")
                    String baseUrl,
            @Value("${knowledge.embedding.ollama.model-name:bge-m3}") String modelName,
            @Value("${knowledge.embedding.ollama.api-key:ollama}") String apiKey) {
        log.info("Initializing Ollama embedding model: {} at {}", modelName, baseUrl);
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}
