package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.application.converter.TenantVoConverter;
import com.mouhin.knowledge.repository.client.dto.TenantCreateCmd;
import com.mouhin.knowledge.repository.client.dto.TenantVO;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台端开通租户命令执行器（app 层用例，事务边界，P3-T1）。
 *
 * <p>本期（解耦子集）落「创建即 {@link Tenant#STATUS_ACTIVE}」的直接开通：校验 {@code tenantKey} 合法性（引用 domain 单一事实源
 * {@link TenantKey}，与 SILO 集合命名口径一致）→ 查重 → 落库 → 审计。 PENDING → PROVISIONING → ACTIVE/FAILED 的编排状态机由
 * P3-T2 在本执行器之上叠加，届时 {@code status} 初值改为 PENDING 并触发异步开通步骤。
 *
 * <p>不含 LLM / 外部 IO，可安全置于 {@link Transactional} 内（红线 #8）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Component
@Slf4j
public class TenantCreateCmdExe {

    /** 审计动作名。 */
    public static final String ACTION = "TENANT_CREATE";

    /** 未指定套餐时的默认值。 */
    private static final String DEFAULT_PLAN = "FREE";

    private final TenantGateway tenantGateway;
    private final PlatformAuditLog platformAuditLog;

    public TenantCreateCmdExe(TenantGateway tenantGateway, PlatformAuditLog platformAuditLog) {
        this.tenantGateway = tenantGateway;
        this.platformAuditLog = platformAuditLog;
    }

    @Transactional
    public TenantVO execute(TenantCreateCmd cmd) {
        String operator = cmd.getOperatorId();
        if (!TenantKey.isValid(cmd.getTenantKey())) {
            platformAuditLog.logTenantAction(
                    operator, ACTION, cmd.getTenantKey(), "REJECTED", "invalid tenantKey format");
            throw new IllegalArgumentException(
                    "Invalid tenantKey: '"
                            + cmd.getTenantKey()
                            + "'; must match "
                            + TenantKey.PATTERN);
        }
        String tenantKey = TenantKey.normalize(cmd.getTenantKey());
        if (tenantGateway.findByTenantKey(tenantKey).isPresent()) {
            platformAuditLog.logTenantAction(
                    operator, ACTION, tenantKey, "REJECTED", "tenantKey already exists");
            throw new IllegalStateException("Tenant already exists: " + tenantKey);
        }

        Tenant tenant = new Tenant();
        tenant.setTenantKey(tenantKey);
        tenant.setName(cmd.getName());
        tenant.setPlan(
                cmd.getPlan() == null || cmd.getPlan().isBlank() ? DEFAULT_PLAN : cmd.getPlan());
        tenant.setDataRegion(cmd.getDataRegion());
        tenant.setStatus(Tenant.STATUS_ACTIVE);
        tenantGateway.save(tenant);

        platformAuditLog.logTenantAction(
                operator,
                ACTION,
                tenantKey,
                "SUCCESS",
                "created status=ACTIVE plan=" + tenant.getPlan());
        log.info("Platform tenant created: {} (id={})", tenantKey, tenant.getTenantId());
        return TenantVoConverter.toVO(tenant);
    }
}
