package com.mouhin.knowledge.repository.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Embedding 角色 LLM 配置（{@code knowledge.llm.embedding.*}）—— 三段式多模型治理之「向量」。
 *
 * <p>向量模型走 OpenAI 兼容端点（本地 BGE-M3 或 DashScope），独立于 chat 的连接池与超时预算：向量化是短平快高并发调用， read 超时给到 10s
 * 即可快速失败，不与长对话争用同一 dispatcher。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "knowledge.llm.embedding")
public class LlmEmbeddingProperties {

    /** 供应商标识（仅用于状态展示与日志）。 */
    private String provider = "ollama";

    /** OpenAI 兼容 base-url。 */
    private String baseUrl = "http://localhost:11434/v1";

    /** 访问密钥（ollama 可为占位符）。 */
    private String apiKey = "ollama";

    /** 模型名。 */
    private String modelName = "bge-m3";

    /** TCP 建连超时（秒）。 */
    private int connectTimeoutSeconds = 2;

    /** 单次读超时（秒）。 */
    private int readTimeoutSeconds = 10;

    /** 整次调用超时（秒）。 */
    private int callTimeoutSeconds = 15;
}
