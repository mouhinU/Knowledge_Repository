package com.mouhin.knowledge.repository.domain.gateway;

/**
 * 平台端操作审计日志网关（domain 定义，infrastructure 以 SLF4J 实现）。
 *
 * <p>记录平台管理员对租户生命周期的高敏写操作（创建 / 软停 / 恢复 / 归档 / 删除），用于合规追溯与故障复盘。 参照 {@code BlackboardAuditLog} 的「接口在
 * domain、实现在 infrastructure」分层：P3-T1 交付的实现落 SLF4J 结构化日志， Phase 4 视合规要求可平滑替换为 DB 持久化（{@code
 * sys_audit_log}）而不改动 app 层调用点。
 *
 * <p><b>脱敏纪律</b>（红线 #4 / 安全分册）：实现方不得回显令牌、口令等敏感信息；租户键、操作者、动作、结果可记录。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
public interface PlatformAuditLog {

    /**
     * 记录一次平台端租户操作。
     *
     * @param operator  操作者标识（平台管理员 userKey / username，来自平台令牌 claim）
     * @param action    动作名（如 {@code TENANT_CREATE} / {@code TENANT_SUSPEND} / {@code TENANT_RESUME}）
     * @param tenantKey 目标租户业务键
     * @param outcome   结果（{@code SUCCESS} / {@code REJECTED} / {@code FAILED}）
     * @param detail    补充说明（状态迁移、拒绝原因等；实现方须做日志注入清理）
     */
    void logTenantAction(
            String operator, String action, String tenantKey, String outcome, String detail);
}
