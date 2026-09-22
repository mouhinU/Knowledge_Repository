package com.mouhin.knowledge.repository.infrastructure.config;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多模型 per-role HTTP 客户端治理（Phase R1 · 五独立原则）。
 *
 * <p>为 chat / embedding 各提供一套独立超时预算的 {@link HttpClientBuilder}，由 {@code OpenAiChatModel} / {@code
 * OpenAiEmbeddingModel} 显式注入。若不注入，LangChain4j 会在每个模型内部各自 {@code new} 客户端——看似隔离，实则
 * 连接池与线程池分配不可控；集中在此按角色显式建连并配 connect/read 超时，才能做到「一角色慢不饿死另一角色」。
 *
 * <p>vision 角色的 HttpClient bean 与其 {@code knowledge.llm.vision.*} 配置在 Phase B 一并引入。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Configuration
@Slf4j
public class LlmClientConfig {

    /** Chat 角色 HTTP 客户端构造器：connect / read 取自 {@link LlmChatProperties}。 */
    @Bean("chatHttpClientBuilder")
    public HttpClientBuilder chatHttpClientBuilder(LlmChatProperties chat) {
        log.info(
                "Chat HTTP client: connect={}s read={}s",
                chat.getConnectTimeoutSeconds(),
                chat.getReadTimeoutSeconds());
        return new JdkHttpClientBuilder()
                .connectTimeout(Duration.ofSeconds(chat.getConnectTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(chat.getReadTimeoutSeconds()));
    }

    /** Embedding 角色 HTTP 客户端构造器：短超时快速失败，与 chat 隔离。 */
    @Bean("embeddingHttpClientBuilder")
    public HttpClientBuilder embeddingHttpClientBuilder(LlmEmbeddingProperties embedding) {
        log.info(
                "Embedding HTTP client: connect={}s read={}s",
                embedding.getConnectTimeoutSeconds(),
                embedding.getReadTimeoutSeconds());
        return new JdkHttpClientBuilder()
                .connectTimeout(Duration.ofSeconds(embedding.getConnectTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(embedding.getReadTimeoutSeconds()));
    }
}
