package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 单次配额检查结果（P6-T3）。
 *
 * <p>携带租户键、指标、限额、当前用量，供异常响应体与日志使用。
 *
 * @param action       处置动作
 * @param tenantKey    归一化租户键
 * @param metric       指标枚举
 * @param quota        限额（-1 = 无限）
 * @param currentUsage 当前用量
 */
public record QuotaCheckResult(
        QuotaAction action, String tenantKey, UsageMetric metric, long quota, long currentUsage) {

    private static final QuotaAction[] SEVERITY = {
            QuotaAction.PASS, QuotaAction.ALERT, QuotaAction.THROTTLE, QuotaAction.BLOCK
    };

    /**
     * 合并两个检查结果，取最严策略。
     *
     * @param a 第一个结果（可为 null）
     * @param b 第二个结果（可为 null）
     * @return 较严的结果；两者均为 null 时返回 null
     */
    public static QuotaCheckResult merge(QuotaCheckResult a, QuotaCheckResult b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return severityOf(a.action) >= severityOf(b.action) ? a : b;
    }

    /**
     * 将 {@link QuotaPolicy} 映射为 {@link QuotaAction}。
     */
    public static QuotaAction actionFromPolicy(QuotaPolicy policy) {
        return switch (policy) {
            case BLOCK -> QuotaAction.BLOCK;
            case THROTTLE -> QuotaAction.THROTTLE;
            case ALERT -> QuotaAction.ALERT;
        };
    }

    private static int severityOf(QuotaAction action) {
        for (int i = 0; i < SEVERITY.length; i++) {
            if (SEVERITY[i] == action) {
                return i;
            }
        }
        return 0;
    }
}
