package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 计量指标白名单（P6-T1）。
 *
 * <p>新增 metric 只需加枚举值 + 对应 infra 边界埋点。 限额表 {@code tenant_plan_limit.metric} 与此枚举 1:1 对应。
 */
public enum UsageMetric {
    CHAT_TOKENS("chat_tokens"),
    EMBEDDING_TOKENS("embedding_tokens"),
    DOC_COUNT("doc_count"),
    STORAGE_BYTES("storage_bytes");

    private final String code;

    UsageMetric(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /**
     * 按 code 反查枚举。
     *
     * @param code 存储/配置中使用的字符串标识
     * @return 对应枚举值
     * @throws IllegalArgumentException 未知 code
     */
    public static UsageMetric fromCode(String code) {
        for (UsageMetric m : values()) {
            if (m.code.equals(code)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown metric: " + code);
    }
}
