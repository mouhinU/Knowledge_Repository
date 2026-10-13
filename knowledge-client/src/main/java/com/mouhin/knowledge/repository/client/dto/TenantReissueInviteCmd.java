package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 平台端重签邀请命令（client 层入参，P3-T7 邀请式设密<b>回访</b>）。
 *
 * <p>用于首位 admin 未在 72h 内消费初始邀请码时，由平台运维触发<b>重新签发</b>：吊销该 (tenantKey, username) 下所有 PENDING 邀请（含已过期的）
 * → 生成新 raw code → 一次性回显给平台运维，由运维通过安全渠道转交租户管理员。
 *
 * <p>与 {@code UserInviteService#issue} 的关系：本命令是 issue 的<b>业务门面</b>——issue 已内建"同用户旧 PENDING
 * 自动吊销"语义（幂等），reissue 只是把 (tenantKey, username) 定位到 (tenantId, userId) 后转调 issue。
 *
 * <p>{@code operatorId} / {@code operatorName} 由 {@code PlatformTokenAuthFilter} 从平台令牌 claim 注入， 用于
 * {@code PlatformAuditLog} 记录"谁为重签负责"；租户管理员本人无法触发（走不通平台令牌）。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Getter
@Setter
public class TenantReissueInviteCmd {

    /**
     * 目标租户业务键（必填）。
     */
    private String tenantKey;

    /**
     * 目标管理员用户名（必填，租户内唯一）。
     */
    private String username;

    /**
     * 邀请有效期（小时）；{@code null} 或 ≤0 时由 {@code UserInviteService.DEFAULT_TTL}（72h）兜底。
     */
    private Integer ttlHours;

    /**
     * 操作者标识（平台令牌 claim 注入，仅审计用）。
     */
    private String operatorId;

    /**
     * 操作者展示名（平台令牌 claim 注入，仅审计用）。
     */
    private String operatorName;
}
