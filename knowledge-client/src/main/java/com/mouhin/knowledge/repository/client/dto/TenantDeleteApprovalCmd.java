package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 平台端租户删除审批令牌签发命令（client 层入参，P3-T5：S-4=方案 A 双人复核第一步）。
 *
 * <p>由项目负责人签发：为已过保留期的 ARCHIVED 租户生成一次性删除审批令牌，raw token 仅在响应体一次性返回。 {@code ttlHours} 可选覆盖默认有效期（缺省
 * 24h）；{@code operatorId}/{@code operatorName} 由平台令牌 claim 注入。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Getter
@Setter
public class TenantDeleteApprovalCmd {

    /** 目标租户业务键。 */
    private String tenantKey;

    /** 令牌有效期（小时）；{@code null} 或 ≤0 时用默认 24h。 */
    private Integer ttlHours;

    /** 签发者标识（项目负责人，来自平台令牌 claim）。 */
    private String operatorId;

    /** 签发者展示名（来自平台令牌 claim）。 */
    private String operatorName;
}
