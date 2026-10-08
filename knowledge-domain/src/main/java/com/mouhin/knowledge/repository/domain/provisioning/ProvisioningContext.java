package com.mouhin.knowledge.repository.domain.provisioning;

import com.mouhin.knowledge.repository.domain.model.entity.Tenant;

/**
 * 租户开通上下文（P3-T2）。
 *
 * <p>在 {@link ProvisioningStep} 之间传递的只读载体：承载目标租户、本次请求的 UUID v4 幂等键与操作者身份。
 * 步骤实现从中读取所需信息，不应修改上下文本身（租户状态迁移由 {@code ProvisioningStateMachine} 统一编排）。
 *
 * @param tenant 目标租户聚合根（含 tenantId / tenantKey / status）
 * @param provisionRequestId 本次开通请求 UUID v4（S-5=方案 A 幂等键）
 * @param operatorId 操作者标识（平台端令牌携带）
 * @param operatorName 操作者显示名
 * @author mouhinU
 * @date 2026-10-08
 */
public record ProvisioningContext(
        Tenant tenant, String provisionRequestId, String operatorId, String operatorName) {

    /** 便捷取租户主键。 */
    public Long tenantId() {
        return tenant == null ? null : tenant.getTenantId();
    }

    /** 便捷取租户业务键。 */
    public String tenantKey() {
        return tenant == null ? null : tenant.getTenantKey();
    }
}
