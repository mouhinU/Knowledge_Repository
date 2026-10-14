package com.mouhin.knowledge.repository.domain.constant;

/**
 * OpenAI 兼容协议 JSON 请求体字段名与已知取值常量。
 *
 * <p>infra 层 {@code OpenAiCompatibleVisionChatGateway} / {@code
 * OpenAiCompatibleStreamingChatGateway} / {@code SimpleHttpScoringModel} 三处直连 LLM HTTP
 * 端点，请求体键名与角色/内容类型取值均为协议契约。 依 AGENTS.md 红线 #2 收敛为常量集中维护。
 */
public final class OpenAiProtocol {

    private OpenAiProtocol() {}

    // ── 顶层请求体字段 ────────────────────────────────────

    /** 顶层 {@code model} 字段。 */
    public static final String KEY_MODEL = "model";

    /** 顶层 {@code temperature} 字段。 */
    public static final String KEY_TEMPERATURE = "temperature";

    /** 顶层 {@code max_tokens} 字段。 */
    public static final String KEY_MAX_TOKENS = "max_tokens";

    /** 顶层 {@code stream} 字段（流式开关）。 */
    public static final String KEY_STREAM = "stream";

    /** 顶层 {@code messages} 数组字段。 */
    public static final String KEY_MESSAGES = "messages";

    // ── 消息项字段 ────────────────────────────────────────

    /** 消息项 {@code role} 字段。 */
    public static final String KEY_ROLE = "role";

    /** 消息项 {@code content} 字段。 */
    public static final String KEY_CONTENT = "content";

    /** 多模态内容项 {@code type} 字段。 */
    public static final String KEY_TYPE = "type";

    /** 多模态图片项 {@code image_url} 嵌套对象字段。 */
    public static final String KEY_IMAGE_URL = "image_url";

    /** 图片对象内 {@code url} 字段。 */
    public static final String KEY_URL = "url";

    /** 多模态文本项 {@code text} 字段（与 {@link #VALUE_TYPE_TEXT} 同值不同语义：一个是键，一个是取值）。 */
    public static final String KEY_TEXT = "text";

    // ── 已知取值 ──────────────────────────────────────────

    /** {@code role} 取值：系统提示。 */
    public static final String VALUE_ROLE_SYSTEM = "system";

    /** {@code role} 取值：用户消息。 */
    public static final String VALUE_ROLE_USER = "user";

    /** {@code role} 取值：模型返回。 */
    public static final String VALUE_ROLE_ASSISTANT = "assistant";

    /** {@code type} 取值：文本段。 */
    public static final String VALUE_TYPE_TEXT = "text";

    /** {@code type} 取值：图片段。 */
    public static final String VALUE_TYPE_IMAGE_URL = "image_url";

    // ── 响应体字段（OpenAI compatible /v1/chat/completions）───

    /** 响应顶层 {@code choices} 数组字段。 */
    public static final String KEY_CHOICES = "choices";

    /** {@code choices[]} 元素内 {@code message} 字段（非流式）。 */
    public static final String KEY_MESSAGE = "message";

    /** {@code choices[]} 元素内 {@code delta} 字段（流式增量）。 */
    public static final String KEY_DELTA = "delta";

    /** {@code choices[]} 元素内 {@code finish_reason} 字段。 */
    public static final String KEY_FINISH_REASON = "finish_reason";

    /** 响应顶层 {@code error} 对象字段。 */
    public static final String KEY_ERROR = "error";

    /** {@code error} 内 {@code message} 字段。 */
    public static final String KEY_ERROR_MESSAGE = "message";

    /** {@code delta}/{@code message} 内 {@code reasoning_content} 字段（思考型模型扩展）。 */
    public static final String KEY_REASONING_CONTENT = "reasoning_content";

    /** {@code delta}/{@code message} 内 {@code reasoning} 字段（部分模型别名）。 */
    public static final String KEY_REASONING = "reasoning";
}
