package com.mouhin.knowledge.repository.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Phase R1 三段式模型装配的容器接线测试。
 *
 * <p>用 {@link ApplicationContextRunner} 在最小上下文中加载 {@link LlmClientConfig} / {@link ChatModelConfig}
 * / {@link EmbeddingModelConfig}，验证：① chat / embedding 各自的角色 HTTP 客户端 Bean 均创建且彼此不同（连接隔离）； ②
 * {@code @Qualifier} 正确解析（无 HttpClientBuilder 类型歧义）；③ {@link ChatModel} / {@link EmbeddingModel} /
 * 流式网关 Bean 能凭三段式配置成功构建。构建期不触发网络调用。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@DisplayName("R1 多模型配置装配")
class LlmClientConfigTest {

    @Configuration
    @EnableConfigurationProperties({LlmChatProperties.class, LlmEmbeddingProperties.class})
    @Import({LlmClientConfig.class, ChatModelConfig.class, EmbeddingModelConfig.class})
    static class TestConfig {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class)
                    .withPropertyValues(
                            "knowledge.llm.chat.provider=deepseek",
                            "knowledge.llm.chat.base-url=https://api.deepseek.com",
                            "knowledge.llm.chat.api-key=test-chat-key",
                            "knowledge.llm.chat.model-name=deepseek-flash",
                            "knowledge.llm.chat.connect-timeout-seconds=5",
                            "knowledge.llm.chat.read-timeout-seconds=120",
                            "knowledge.llm.chat.call-timeout-seconds=180",
                            "knowledge.llm.embedding.provider=ollama",
                            "knowledge.llm.embedding.base-url=http://localhost:11434/v1",
                            "knowledge.llm.embedding.api-key=test-embed-key",
                            "knowledge.llm.embedding.model-name=bge-m3",
                            "knowledge.llm.embedding.connect-timeout-seconds=2",
                            "knowledge.llm.embedding.read-timeout-seconds=10",
                            "knowledge.llm.embedding.call-timeout-seconds=15");

    @Test
    @DisplayName("per-role HttpClientBuilder 均创建且互不相同（连接隔离）")
    void perRoleHttpClientsAreDistinct() {
        runner.run(
                ctx -> {
                    assertThat(ctx).hasSingleBean(LlmClientConfig.class);
                    HttpClientBuilder chat =
                            ctx.getBean("chatHttpClientBuilder", HttpClientBuilder.class);
                    HttpClientBuilder embedding =
                            ctx.getBean("embeddingHttpClientBuilder", HttpClientBuilder.class);
                    assertThat(chat).isNotNull();
                    assertThat(embedding).isNotNull();
                    assertThat(chat).isNotSameAs(embedding);
                });
    }

    @Test
    @DisplayName("ChatModel / EmbeddingModel / 流式网关凭三段式配置成功构建，Qualifier 无歧义")
    void modelBeansWired() {
        runner.run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(ChatModel.class);
                    assertThat(ctx).hasSingleBean(EmbeddingModel.class);
                    assertThat(ctx)
                            .hasSingleBean(
                                    com.mouhin.knowledge.repository.domain.service
                                            .StreamingChatGateway.class);
                });
    }
}
