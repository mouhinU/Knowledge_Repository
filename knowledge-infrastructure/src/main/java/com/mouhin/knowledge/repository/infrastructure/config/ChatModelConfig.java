package com.mouhin.knowledge.repository.infrastructure.config;

import com.mouhin.knowledge.repository.domain.service.StreamingChatGateway;
import com.mouhin.knowledge.repository.infrastructure.llm.LlmResilience;
import com.mouhin.knowledge.repository.infrastructure.llm.OpenAiCompatibleStreamingChatGateway;
import com.mouhin.knowledge.repository.infrastructure.llm.ResilientChatModel;
import com.mouhin.knowledge.repository.infrastructure.llm.ResilientStreamingChatGateway;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chat 角色模型装配（Phase R1 三段式）。
 *
 * <p>不再按 provider 开关建多套 Bean：供应商由 {@link LlmChatProperties} 的 base-url / api-key / model-name 直接决定，
 * 统一以 OpenAI 兼容协议构建唯一 {@link ChatModel}，并注入 chat 角色的 {@link HttpClientBuilder}（connect/read 隔离）与
 * call 超时。同时装配黑板 / 出卷使用的流式对话网关，沿用同一端点但放宽 token 预算与超时。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Configuration
@Slf4j
public class ChatModelConfig {

    /** Chat 对话模型（非流式），注入 chat 角色独立 HTTP 客户端，并套 {@code chat} 角色熔断/重试。 */
    @Bean
    public ChatModel chatModel(
            LlmChatProperties chat,
            LlmResilience resilience,
            @Qualifier("chatHttpClientBuilder") HttpClientBuilder httpClientBuilder) {
        log.info(
                "Initializing chat model: {} at {} (provider={})",
                chat.getModelName(),
                chat.getBaseUrl(),
                chat.getProvider());
        ChatModel raw =
                OpenAiChatModel.builder()
                        .baseUrl(chat.getBaseUrl())
                        .apiKey(chat.getApiKey())
                        .modelName(chat.getModelName())
                        .temperature(chat.getTemperature())
                        .maxTokens(chat.getMaxTokens())
                        .httpClientBuilder(httpClientBuilder)
                        .timeout(Duration.ofSeconds(Math.max(chat.getCallTimeoutSeconds(), 30)))
                        .build();
        return new ResilientChatModel(raw, resilience);
    }

    /**
     * 流式对话网关（黑板 Agent / 出卷逐 token 推送）。
     *
     * <p>与 {@link #chatModel} 共用同一端点与密钥，但改用直连 SSE 请求以同时拿到正式输出与思考链增量；token 预算与超时 从 {@link
     * LlmChatProperties.Streaming} 取，明显大于非流式，避免推理型模型把预算耗在思考链上导致正文为空。
     */
    @Bean
    public StreamingChatGateway streamingChatGateway(
            LlmChatProperties chat, LlmResilience resilience) {
        LlmChatProperties.Streaming streaming = chat.getStreaming();
        Duration timeout = Duration.ofSeconds(Math.max(streaming.getTimeoutSeconds(), 60));
        log.info(
                "Initializing streaming chat gateway: {} at {} (maxTokens={})",
                chat.getModelName(),
                chat.getBaseUrl(),
                streaming.getMaxTokens());
        StreamingChatGateway rawGateway =
                new OpenAiCompatibleStreamingChatGateway(
                        chat.getBaseUrl(),
                        chat.getApiKey(),
                        chat.getModelName(),
                        chat.getTemperature(),
                        streaming.getMaxTokens(),
                        timeout);
        return new ResilientStreamingChatGateway(rawGateway, resilience);
    }
}
