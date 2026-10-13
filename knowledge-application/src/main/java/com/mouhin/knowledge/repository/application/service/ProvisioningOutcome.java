package com.mouhin.knowledge.repository.application.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一次租户开通编排的结果快照（P3-T2 引入，P3-T3 扩展 {@link InviteInfo}）。
 *
 * <p>由 {@link ProvisioningStateMachine#provision} 返回，供 {@code TenantProvisionCmdExe} 转 VO。
 * 不落库，仅进程内传递。
 *
 * @param provisionRequestId 本次请求 UUID v4（S-5=方案 A 幂等键）
 * @param finalStatus 租户终态（{@code ACTIVE} / {@code FAILED}）
 * @param steps 各步骤执行结果，按执行顺序
 * @param invite 首位 admin 邀请信息（P3-T3，仅在 {@code finalStatus=ACTIVE} 且 {@code CreateFirstAdminStep}
 *     实际执行时非空；重放 SKIPPED 场景为 {@code null}——旧邀请已被后续调用重签或已消费）
 * @author mouhinU
 * @date 2026-10-08
 */
public record ProvisioningOutcome(
        String provisionRequestId, String finalStatus, List<StepOutcome> steps, InviteInfo invite) {

    /** 兼容构造：无邀请信息（P3-T2 遗留调用点或 FAILED / ACTIVE 幂等短路场景）。 */
    public ProvisioningOutcome(
            String provisionRequestId, String finalStatus, List<StepOutcome> steps) {
        this(provisionRequestId, finalStatus, steps, null);
    }

    /** 是否开通成功（终态 ACTIVE）。 */
    public boolean isSuccess() {
        return "ACTIVE".equals(finalStatus);
    }

    /**
     * 单步骤执行结果。
     *
     * @param stepName 步骤名
     * @param status {@code SUCCESS} / {@code FAILED} / {@code SKIPPED}（重放时前次已成功）
     * @param attemptNo 本次尝试序号
     * @param errorDetail 失败原因（成功 / 跳过为 {@code null}）
     */
    public record StepOutcome(String stepName, String status, int attemptNo, String errorDetail) {}

    /**
     * 首位 admin 邀请信息（P3-T3，一次性回显给平台运维，随后由运维口头发给租户管理员）。
     *
     * <p><b>安全纪律</b>：{@code code} 是 raw invite code，仅在本次 provision HTTP 响应体出现一次， <b>不</b>落库（DB 只存其
     * SHA-256 hash）、<b>不</b>入日志。caller 收到后应立即复制给运维并放弃本地引用。
     *
     * @param code 原始邀请码（Base64URL 无填充，43 字符）
     * @param url 前端设密页相对 URL：{@code /set-password.html?code=<code>}
     * @param expiresAt 过期时间（默认签发 +72h）
     * @param adminUsername 首位 admin 的用户名（如 {@code acme-admin}），便于运维在邮件 / IM 里明示
     */
    public record InviteInfo(
            String code, String url, LocalDateTime expiresAt, String adminUsername) {}
}
