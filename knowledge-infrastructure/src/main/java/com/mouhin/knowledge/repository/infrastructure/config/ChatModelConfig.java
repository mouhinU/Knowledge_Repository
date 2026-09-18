package com.mouhin.knowledge.repository.infrastructure.config;

import com.mouhin.knowledge.repository.domain.service.StreamingChatGateway;
import com.mouhin.knowledge.repository.infrastructure.llm.OpenAiCompatibleStreamingChatGateway;
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

    /**
     * 流式对话网关（供黑板 Agent 与主观题评分逐 token 推送使用）。
     * <p>
     * 与上方三个 {@link ChatModel} Bean 使用完全一致的 provider 配置，只是改为
     * 直接以 {@code stream=true} 请求 OpenAI 兼容端点，从而能同时拿到正式输出与
     * 思考链增量。dashscope / deepseek / ollama 共用同一实现，仅参数不同。
     * </p>
     */
    @Bean
    @ConditionalOnProperty(name = "knowledge.llm.provider")
    public StreamingChatGateway streamingChatGateway(
            @Value("${knowledge.llm.provider}") String provider,
            @Value("${knowledge.llm.temperature:0.7}") double temperature,
            @Value("${knowledge.llm.streaming.max-tokens:16384}") int maxTokens,
            @Value("${knowledge.llm.streaming.timeout-seconds:300}") int timeoutSeconds,
            @Value("${knowledge.llm.dashscope.api-key:}") String dashscopeApiKey,
            @Value("${knowledge.llm.dashscope.model-name:qwen-plus}") String dashscopeModel,
            @Value("${knowledge.llm.deepseek.api-key:}") String deepseekApiKey,
            @Value("${knowledge.llm.deepseek.model-name:deepseek-chat}") String deepseekModel,
            @Value("${knowledge.llm.ollama.base-url:http://localhost:11434/v1}") String ollamaBaseUrl,
            @Value("${knowledge.llm.ollama.model-name:llama3.2}") String ollamaModel,
            @Value("${knowledge.llm.ollama.api-key:ollama}") String ollamaApiKey) {
        // 流式网关独立放宽 token 预算与超时：推理型模型（如 deepseek-flash）会先把
        // max_tokens 消耗在思考链上，预算过小会导致正式内容为空，故默认给到 16384；
        // 超时统一取配置值（默认 300s），避免长思考请求被 120s 提前掐断。
        Duration timeout = Duration.ofSeconds(Math.max(timeoutSeconds, 60));
        return switch (provider == null ? "" : provider.toLowerCase()) {
            case "dashscope" -> new OpenAiCompatibleStreamingChatGateway(
                    "https://dashscope.aliyuncs.com/compatible-mode/v1", dashscopeApiKey, dashscopeModel,
                    temperature, maxTokens, timeout);
            case "deepseek" -> new OpenAiCompatibleStreamingChatGateway(
                    "https://api.deepseek.com", deepseekApiKey, deepseekModel,
                    temperature, maxTokens, timeout);
            case "ollama" -> new OpenAiCompatibleStreamingChatGateway(
                    ollamaBaseUrl, ollamaApiKey, ollamaModel,
                    temperature, maxTokens, timeout);
            default -> throw new IllegalStateException(
                    "未知的 knowledge.llm.provider，无法初始化流式对话网关: " + provider);
        };
    }
}
