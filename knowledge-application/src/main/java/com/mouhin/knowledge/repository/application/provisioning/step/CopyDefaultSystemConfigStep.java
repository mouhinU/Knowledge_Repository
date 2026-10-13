package com.mouhin.knowledge.repository.application.provisioning.step;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.gateway.SystemConfigGateway;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.provisioning.ProvisioningContext;
import com.mouhin.knowledge.repository.domain.provisioning.ProvisioningStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 拷贝默认系统配置步骤（P3-T3，order=200）。
 *
 * <p>把 {@link TenantContext#DEFAULT_TENANT_KEY default 租户}（V23 种子 id=1）下的全部 {@code sys_config}
 * 行拷贝到新开通租户，实现"新租户首次登录即看到默认值"（执行计划 §5 P3-T3 验收口径）。
 *
 * <p><b>幂等</b>：{@link SystemConfigGateway#copyAllFromTenantToTenant} 逐行以 {@code UNIQUE(tenant_id,
 * config_key)}（V28）为兜底、SELECT-then-INSERT 前置查重；重放已 SUCCESS 由状态机跳过、 未 SUCCESS 时重跑亦无副作用叠加。
 *
 * <p><b>为何不用 {@code TenantContext} 定位源租户</b>：本步骤运行在平台端 provisioning 上下文， {@code TenantContext}
 * 是平台调用者身份（default 或未绑定），非目标租户。源租户固定为 default（V23 种子）， 目标租户从 {@link ProvisioningContext#tenantId()}
 * 取，均绕过 ThreadLocal。
 *
 * <p><b>自拷贝防御</b>：若目标租户就是 default（异常场景：给 default 重跑 provision），静默跳过——避免 无意义的 SELECT+INSERT
 * 循环，也避免与"平台运维手动改过 default 配置"的现网数据竞争。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Component
public class CopyDefaultSystemConfigStep implements ProvisioningStep {

    /**
     * 步骤名常量，落 {@code sys_tenant_provision_step.step_name}，须稳定不可改名。
     */
    public static final String STEP_NAME = "COPY_DEFAULT_SYS_CONFIG";
    /**
     * 排序权重：在 {@code ValidateTenantStep(100)} 之后、根部门创建之前。
     */
    public static final int STEP_ORDER = 200;
    private static final Logger log = LoggerFactory.getLogger(CopyDefaultSystemConfigStep.class);
    private final SystemConfigGateway systemConfigGateway;
    private final TenantGateway tenantGateway;

    public CopyDefaultSystemConfigStep(
            SystemConfigGateway systemConfigGateway, TenantGateway tenantGateway) {
        this.systemConfigGateway = systemConfigGateway;
        this.tenantGateway = tenantGateway;
    }

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
        Long targetTenantId = context.tenantId();
        if (targetTenantId == null) {
            throw new IllegalStateException("Target tenantId is null; cannot copy sys_config");
        }
        Tenant defaultTenant =
                tenantGateway
                        .findByTenantKey(TenantContext.DEFAULT_TENANT_KEY)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Default tenant '"
                                                        + TenantContext.DEFAULT_TENANT_KEY
                                                        + "' not found; sys_config seed missing (V23)"));
        Long sourceTenantId = defaultTenant.getTenantId();
        if (Objects.equals(sourceTenantId, targetTenantId)) {
            log.info(
                    "[Provisioning] CopyDefaultSystemConfig skipped: target tenant IS the default tenant (id={})",
                    targetTenantId);
            return;
        }
        int copied = systemConfigGateway.copyAllFromTenantToTenant(sourceTenantId, targetTenantId);
        log.info(
                "[Provisioning] CopyDefaultSystemConfig done: source={} target={} copiedRows={}",
                sourceTenantId,
                targetTenantId,
                copied);
    }
}
