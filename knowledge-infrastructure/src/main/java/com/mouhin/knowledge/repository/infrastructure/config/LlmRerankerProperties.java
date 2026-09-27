package com.mouhin.knowledge.repository.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Reranker 角色 LLM 配置（{@code knowledge.llm.reranker.*}）—— 检索后重排。
 *
 * <p>Reranker 模型（cross-encoder）对 (query, document) 对做精准相关性打分，区分度远高于 bi-encoder 余弦相似度。 检索
 * pipeline：Milvus 向量检索 top-K → reranker 重排 → 取 top-N 给 LLM。
 *
 * <p>支持 OpenAI 兼容的 scoring 端点（如 Xenova/bge-reranker、Infinity、TEI 等）。 默认关闭（enabled=false），需显式启用并配置
 * base-url/model-name。
 *
 * @author mouhinU
 * @date 2026-09-25
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "knowledge.llm.reranker")
public class LlmRerankerProperties {

    /** 是否启用 reranker。 */
    private boolean enabled = false;

    /** 供应商标识（仅用于日志）。 */
    private String provider = "openai-compatible";

    /** OpenAI 兼容 scoring base-url。 */
    private String baseUrl = "http://localhost:11434/v1";

    /** 访问密钥。 */
    private String apiKey = "ollama";

    /** 模型名（如 bge-reranker-v2-m3）。 */
    private String modelName = "bge-reranker-v2-m3";

    /** TCP 建连超时（秒）。 */
    private int connectTimeoutSeconds = 2;

    /** 单次读超时（秒）。 */
    private int readTimeoutSeconds = 10;

    /** 整次调用超时（秒）。 */
    private int callTimeoutSeconds = 30;

    /** 重排后保留的 top-N 数量（传给 LLM 的最终结果数）。 */
    private int topN = 10;
}
