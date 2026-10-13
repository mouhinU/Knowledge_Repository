package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 套餐限额值对象（P6-T2）。
 *
 * <p>对应 {@code tenant_plan_limit} 表的一行。
 *
 * @param metric 指标枚举
 * @param quota  限额绝对值；{@code -1} 表示无限
 * @param policy 超限策略
 */
public record TenantPlanLimit(UsageMetric metric, long quota, QuotaPolicy policy) {

    /**
     * 是否无限额。
     */
    public boolean isUnlimited() {
        return quota < 0;
    }
}
