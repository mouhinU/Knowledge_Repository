package com.mouhin.knowledge.repository.domain.constant;

/**
 * 平台审计结果常量。
 *
 * <p>{@code PlatformAuditLog#logTenantAction} 的 outcome 维度词表，统一在此维护，禁止在编排层硬编码字符串字面量 （AGENTS.md 红线
 * #2）。此前散落在 {@code executor/platform/*}、{@code executor/adminauth/*}、{@code service/*} 约 35 处。
 */
public final class AuditOutcomes {

    private AuditOutcomes() {}

    /** 操作成功。 */
    public static final String SUCCESS = "SUCCESS";

    /** 操作被拒绝（前置校验/权限/幂等未通过）。 */
    public static final String REJECTED = "REJECTED";

    /** 操作执行失败（异常/下游错误）。 */
    public static final String FAILED = "FAILED";
}
