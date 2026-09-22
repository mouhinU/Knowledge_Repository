package com.mouhin.knowledge.repository.infrastructure.config;

import com.mouhin.knowledge.repository.domain.gateway.VisionChatGateway;
import com.mouhin.knowledge.repository.infrastructure.llm.OpenAiCompatibleVisionChatGateway;
import java.net.http.HttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 视觉角色模型装配（Phase B）。
 *
 * <p>为视觉构建<b>独立</b>的 JDK {@link HttpClient}（连接/读超时来自 {@link LlmVisionProperties}，HTTP/1.1 以兼容大
 * base64 请求体经反代的场景），并据此装配唯一 {@link VisionChatGateway}。视觉默认 {@code enabled=false}，网关仍会创建（Bean
 * 就绪），但是否参与解析由 {@link
 * com.mouhin.knowledge.repository.infrastructure.extractor.VisionModelExtractionStrategy} 的 {@code
 * supports()} 依据开关决定。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Configuration
@Slf4j
public class VisionModelConfig {

    /** 视觉角色专属 HTTP 客户端，与 chat / embedding 连接池隔离。 */
    @Bean("visionHttpClient")
    public HttpClient visionHttpClient(LlmVisionProperties vision) {
        log.info(
                "Vision HTTP client: connect={}s (mode={}, enabled={})",
                vision.getConnectTimeoutSeconds(),
                vision.getMode(),
                vision.isEnabled());
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(vision.getConnectTimeoutSeconds(), 1)))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /** 视觉对话网关，注入视觉角色独立 HTTP 客户端与整调用超时。 */
    @Bean
    public VisionChatGateway visionChatGateway(
            LlmVisionProperties vision, @Qualifier("visionHttpClient") HttpClient httpClient) {
        Duration timeout = Duration.ofSeconds(Math.max(vision.getCallTimeoutSeconds(), 30));
        return new OpenAiCompatibleVisionChatGateway(
                vision.getBaseUrl(),
                vision.getApiKey(),
                vision.getModelName(),
                vision.getTemperature(),
                vision.getMaxTokens(),
                timeout,
                httpClient);
    }
}
