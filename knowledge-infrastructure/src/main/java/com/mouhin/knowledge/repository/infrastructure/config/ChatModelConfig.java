package com.mouhin.knowledge.repository.infrastructure.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LLM 对话模型配置
 * <p>
 * 通过 knowledge.llm.provider 配置项切换对话模型提供商：
 * <ul>
 *     <li>dashscope — 阿里云通义 DashScope（OpenAI 兼容端点）</li>
 *     <li>deepseek — DeepSeek（OpenAI 兼容端点）</li>
 *     <li>ollama — 本地 Ollama（OpenAI 兼容接口）</li>
 * </ul>
 *
 * @author Knowledge-Repository
 * @date 2026-09-12
 */
@Configuration
public class ChatModelConfig {

    private static final Logger logger = LoggerFactory.getLogger(ChatModelConfig.class);

    /**
     * DashScope 对话模型
     */
    @Bean
    @ConditionalOnProperty(name = "knowledge.llm.provider", havingValue = "dashscope")
    public ChatModel dashScopeChatModel(
            @Value("${knowledge.llm.dashscope.api-key}") String apiKey,
            @Value("${knowledge.llm.dashscope.model-name:qwen-plus}") String modelName,
            @Value("${knowledge.llm.temperature:0.7}") double temperature,
            @Value("${knowledge.llm.max-tokens:4096}") int maxTokens) {
        String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        logger.info("Initializing DashScope chat model: {} at {}", modelName, baseUrl);
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .apiKey(apiKey)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(120))
                .maxTokens(maxTokens)
                .build();
    }

    /**
     * DeepSeek 对话模型
     */
    @Bean
    @ConditionalOnProperty(name = "knowledge.llm.provider", havingValue = "deepseek")
    public ChatModel deepSeekChatModel(
            @Value("${knowledge.llm.deepseek.api-key}") String apiKey,
            @Value("${knowledge.llm.deepseek.model-name:deepseek-chat}") String modelName,
            @Value("${knowledge.llm.temperature:0.7}") double temperature,
            @Value("${knowledge.llm.max-tokens:4096}") int maxTokens) {
        String baseUrl = "https://api.deepseek.com";
        logger.info("Initializing DeepSeek chat model: {} at {}", modelName, baseUrl);
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .apiKey(apiKey)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(120))
                .maxTokens(maxTokens)
                .build();
    }

    /**
     * Ollama 对话模型
     */
    @Bean
    @ConditionalOnProperty(name = "knowledge.llm.provider", havingValue = "ollama")
    public ChatModel ollamaChatModel(
            @Value("${knowledge.llm.ollama.base-url:http://localhost:11434/v1}") String baseUrl,
            @Value("${knowledge.llm.ollama.model-name:llama3.2}") String modelName,
            @Value("${knowledge.llm.ollama.api-key:ollama}") String apiKey,
            @Value("${knowledge.llm.temperature:0.7}") double temperature,
            @Value("${knowledge.llm.max-tokens:4096}") int maxTokens) {
        logger.info("Initializing Ollama chat model: {} at {}", modelName, baseUrl);
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .apiKey(apiKey)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(300))
                .maxTokens(maxTokens)
                .build();
    }
}
