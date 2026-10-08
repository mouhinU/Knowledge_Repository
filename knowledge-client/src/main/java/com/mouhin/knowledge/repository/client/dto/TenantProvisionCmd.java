package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 租户开通命令（client 层入参，P3-T2）。
 *
 * <p>驱动 {@code ProvisioningStateMachine} 走 PENDING → PROVISIONING → ACTIVE/FAILED。
 *
 * <p><b>幂等键</b>（S-5=方案 A）：{@code provisionRequestId} 可由调用方显式传入以复用某次请求（重放）； 留空时执行器复用租户库中已有的 {@code
 * provisionRequestId}（create 时生成的 UUID v4）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Getter
@Setter
public class TenantProvisionCmd {

    /** 目标租户业务键（路径变量注入）。 */
    private String tenantKey;

    /** 开通请求 UUID v4；留空则复用租户已有请求 id（重放语义）。 */
    private String provisionRequestId;

    /** 操作者标识（平台端令牌携带）。 */
    private String operatorId;

    /** 操作者显示名。 */
    private String operatorName;
}
