package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 配额超限策略（P6-T2）。
 *
 * <p>平台管理员在 {@code tenant_plan_limit} 表中为每个 metric 配置策略：
 *
 * <ul>
 *   <li>{@link #BLOCK} — 阻断请求，返回 HTTP 402
 *   <li>{@link #THROTTLE} — 限流，返回 HTTP 429 + Retry-After
 *   <li>{@link #ALERT} — 仅记录日志，不阻断
 * </ul>
 */
public enum QuotaPolicy {
    BLOCK,
    THROTTLE,
    ALERT
}
