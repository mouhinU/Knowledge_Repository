package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * Agent 执行状态枚举。
 *
 * <p>用于 {@code BlackboardMetrics#incrementAgentCount} 的 status 维度，替代此前散落在编排层的裸字符串 （{@code
 * "STARTED"} / {@code "COMPLETE"} / {@code "FAILED"} / {@code "ERROR"}），消除魔法值。
 *
 * <p>枚举名 {@link #name()} 直接作为 Prometheus {@code status} 标签值，与历史指标标签保持字节级一致， 不影响既有 Grafana 面板与告警查询。
 *
 * @author mouhinU
 * @date 2026-10-14
 */
public enum AgentExecutionStatus {

    /** Agent 开始执行 */
    STARTED,

    /** Agent 正常完成 */
    COMPLETE,

    /** Agent 执行过程中抛出异常（装饰器捕获） */
    FAILED,

    /** 流水线级错误（编排层兜底捕获） */
    ERROR
}
