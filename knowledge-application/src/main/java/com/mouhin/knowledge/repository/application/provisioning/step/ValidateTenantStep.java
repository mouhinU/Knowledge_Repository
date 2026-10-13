package com.mouhin.knowledge.repository.application.provisioning.step;

import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantKey;
import com.mouhin.knowledge.repository.domain.provisioning.ProvisioningContext;
import com.mouhin.knowledge.repository.domain.provisioning.ProvisioningStep;
import org.springframework.stereotype.Component;

/**
 * 开通前置校验步骤（P3-T2 基线步骤，order=100）。
 *
 * <p>纯校验、无副作用，天然幂等：确认租户已落库（{@code tenantId} 非空）、{@code tenantKey} 仍符合严格规范、 且当前处于可开通态。任一不满足抛 {@link
 * IllegalStateException}，由状态机捕获置 FAILED。
 *
 * <p>P3-T3 的实际副作用步骤（拷贝默认 sys_config / 建根部门 / 建首位 admin）将以更大的 order 插入本步骤之后。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Component
public class ValidateTenantStep implements ProvisioningStep {

    /**
     * 步骤名常量，落 {@code sys_tenant_provision_step.step_name}，须稳定不可改名。
     */
    public static final String STEP_NAME = "VALIDATE_TENANT";

    /**
     * 排序权重：所有开通步骤最先执行。
     */
    public static final int STEP_ORDER = 100;

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public int order() {
        return STEP_ORDER;
    }

    @Override
    public void execute(ProvisioningContext context) {
        Tenant tenant = context.tenant();
        if (tenant == null || tenant.getTenantId() == null) {
            throw new IllegalStateException("Tenant row not persisted, cannot provision");
        }
        if (!TenantKey.isValid(tenant.getTenantKey())) {
            throw new IllegalStateException(
                    "Invalid tenantKey for provisioning: " + tenant.getTenantKey());
        }
        // beginProvisioning 已把状态迁到 PROVISIONING，此处确认迁移确实生效（防御性）
        if (!tenant.isProvisioning()) {
            throw new IllegalStateException(
                    "Tenant '" + tenant.getTenantKey() + "' not in PROVISIONING state");
        }
    }
}
