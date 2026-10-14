package com.mouhin.knowledge.repository.domain.constant;

/**
 * Cohere 兼容 Reranker HTTP API 协议字段名常量。
 *
 * <p>infra 层 {@code SimpleHttpScoringModel} 直连 {@code /v1/rerank} 端点，请求体与响应体 键名均属 Cohere 兼容协议契约。依
 * AGENTS.md 红线 #2 集中维护。
 */
public final class CohereProtocol {

    private CohereProtocol() {}

    // ── 请求体 ──────────────────────────────────────────

    /** 顶层 {@code model} 字段（与 OpenAI 语义一致，此处单列以便各自演进）。 */
    public static final String KEY_MODEL = "model";

    /** 顶层 {@code query} 字段。 */
    public static final String KEY_QUERY = "query";

    /** 顶层 {@code top_n} 字段（返回前 N 条最相关文档）。 */
    public static final String KEY_TOP_N = "top_n";

    /** 顶层 {@code documents} 数组字段。 */
    public static final String KEY_DOCUMENTS = "documents";

    // ── 响应体 ──────────────────────────────────────────

    /** 顶层 {@code results} 数组字段。 */
    public static final String KEY_RESULTS = "results";

    /** {@code results[]} 元素内 {@code index} 字段（回指原数组下标）。 */
    public static final String KEY_INDEX = "index";

    /** {@code results[]} 元素内 {@code relevance_score} 字段。 */
    public static final String KEY_RELEVANCE_SCORE = "relevance_score";
}
