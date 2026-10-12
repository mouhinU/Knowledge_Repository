package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.UsageMetric;
import java.time.LocalDate;

/**
 * 计量采集端口（P6-T1）。
 *
 * <p>计数器语义：{@link #record} 只增不减，采集失败仅记 WARN 不阻塞业务。 实现方须保证并发安全（同一租户同一 metric 可能多线程并发调用）。
 */
public interface TenantUsageRecorder {

    /**
     * 原子递增。首次调用（无现有行）时自动 seed。
     *
     * @param tenantKey 归一化租户键（小写）
     * @param metric 指标枚举
     * @param amount 增量（≤0 静默忽略）
     */
    void record(String tenantKey, UsageMetric metric, long amount);

    /**
     * 查询指定租户在给定周期的用量。无记录返回 0。
     *
     * @param tenantKey 归一化租户键
     * @param metric 指标枚举
     * @param period 查询周期（通常为当月 1 日）
     * @return 当前用量
     */
    long getCurrentUsage(String tenantKey, UsageMetric metric, LocalDate period);
}
