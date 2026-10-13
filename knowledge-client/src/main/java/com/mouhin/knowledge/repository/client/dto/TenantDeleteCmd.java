package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 平台端租户删除确认命令（client 层入参，P3-T5：S-4=方案 A 双人复核第二步）。
 *
 * <p>由<b>第二操作者</b>（须不同于签发者）持签发时一次性返回的 {@code approvalToken}（raw）确认删除：校验令牌有效（PENDING 且未过期） + 保留期已过 +
 * 确认者 ≠ 签发者，通过后把 ARCHIVED 租户迁入 DELETING 并绑定 {@code deletionRequestId}，交由 {@code
 * TenantDeletionReconciler} 硬删。{@code operatorId}/{@code operatorName} 由平台令牌 claim 注入。
 *
 * <p><b>安全纪律</b>（红线 #4）：{@code approvalToken} 是敏感凭据，禁止回显到日志 / 审计 detail；实现方只按 SHA-256 比对。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Getter
@Setter
public class TenantDeleteCmd {

    /**
     * 目标租户业务键。
     */
    private String tenantKey;

    /**
     * 一次性删除审批令牌（raw，第二操作者持有）；敏感，不落日志。
     */
    private String approvalToken;

    /**
     * 确认者标识（第二操作者，来自平台令牌 claim）。
     */
    private String operatorId;

    /**
     * 确认者展示名（来自平台令牌 claim）。
     */
    private String operatorName;
}
