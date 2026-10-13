package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 租户开通结果视图对象（client 层出参，P3-T2）。
 *
 * <p>承载一次 {@code ProvisioningStateMachine} 编排的终态与各步骤明细，供平台端 {@code POST
 * /api/platform/tenants/{tenantKey}/provision} 响应体使用。FAILED 时 {@code steps} 内含失败步骤的 {@code
 * errorDetail}，满足 S-5=方案 A "FAILED 可查询原因"。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Getter
@Setter
public class TenantProvisionVO {

    /**
     * 目标租户业务键。
     */
    private String tenantKey;

    /**
     * 本次请求 UUID v4（幂等键）。
     */
    private String provisionRequestId;

    /**
     * 租户终态：{@code ACTIVE} / {@code FAILED}。
     */
    private String finalStatus;

    /**
     * 是否开通成功。
     */
    private boolean success;

    /**
     * 各步骤执行结果，按执行顺序。
     */
    private List<StepVO> steps;

    /**
     * 首位 admin 邀请信息（P3-T3）；仅在 {@code success=true} 且 {@code CreateFirstAdminStep} 本次实际执行时非空。
     *
     * <p><b>安全</b>：{@code code} 是 raw invite code，仅本响应体一次性可见，不落库、不入日志。
     * 平台运维收到后应立即通过安全渠道转交租户管理员，管理员访问 {@code url} 完成设密。
     */
    private InviteVO invite;

    /**
     * 单步骤执行结果。
     *
     * @author mouhinU
     * @date 2026-10-08
     */
    @Getter
    @Setter
    public static class StepVO {

        /**
         * 步骤名。
         */
        private String stepName;

        /**
         * {@code SUCCESS} / {@code FAILED} / {@code SKIPPED}。
         */
        private String status;

        /**
         * 本次尝试序号。
         */
        private int attemptNo;

        /**
         * 失败原因（成功 / 跳过为 {@code null}）。
         */
        private String errorDetail;
    }

    /**
     * 首位 admin 邀请信息 VO（P3-T3）。
     *
     * @author mouhinU
     * @date 2026-10-08
     */
    @Getter
    @Setter
    public static class InviteVO {

        /**
         * 原始邀请码（Base64URL 无填充，43 字符）；仅本次响应可见。
         */
        private String code;

        /**
         * 前端设密页相对 URL：{@code /set-password.html?code=<code>}。
         */
        private String url;

        /**
         * 过期时间（ISO-8601 字符串，默认签发 +72h）。
         */
        private String expiresAt;

        /**
         * 首位 admin 用户名（如 {@code acme-admin}），便于运维在邮件 / IM 中明示。
         */
        private String adminUsername;
    }
}
