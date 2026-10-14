package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.application.converter.TenantVoConverter;
import com.mouhin.knowledge.repository.client.dto.TenantCreateCmd;
import com.mouhin.knowledge.repository.client.dto.TenantVO;
import com.mouhin.knowledge.repository.domain.constant.AuditOutcomes;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantKey;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台端开通租户命令执行器（app 层用例，事务边界，P3-T1）。
 *
 * <p>校验 {@code tenantKey} 合法性（引用 domain 单一事实源 {@link TenantKey}，与 SILO 集合命名口径一致）→ 查重 → 以 {@link
 * Tenant#STATUS_PENDING} 落库并生成 UUID v4 {@code provisionRequestId}（S-5=方案 A 幂等键）→ 审计。
 * 创建<b>不</b>直接开通：后续由 {@code TenantProvisionCmdExe} 驱动 {@code ProvisioningStateMachine} 走 PENDING →
 * PROVISIONING → ACTIVE/FAILED。 如此把"写元数据"与"跑开通副作用"解耦，失败可携 {@code provisionRequestId} 重放。
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
                    operator,
                    ACTION,
                    cmd.getTenantKey(),
                    AuditOutcomes.REJECTED,
                    "invalid tenantKey format");
            throw new IllegalArgumentException(
                    "Invalid tenantKey: '"
                            + cmd.getTenantKey()
                            + "'; must match "
                            + TenantKey.PATTERN);
        }
        String tenantKey = TenantKey.normalize(cmd.getTenantKey());
        if (tenantGateway.findByTenantKey(tenantKey).isPresent()) {
            platformAuditLog.logTenantAction(
                    operator,
                    ACTION,
                    tenantKey,
                    AuditOutcomes.REJECTED,
                    "tenantKey already exists");
            throw new IllegalStateException("Tenant already exists: " + tenantKey);
        }

        Tenant tenant = new Tenant();
        tenant.setTenantKey(tenantKey);
        tenant.setName(cmd.getName());
        tenant.setPlan(
                cmd.getPlan() == null || cmd.getPlan().isBlank() ? DEFAULT_PLAN : cmd.getPlan());
        tenant.setDataRegion(cmd.getDataRegion());
        tenant.setStatus(Tenant.STATUS_PENDING);
        tenant.setProvisionRequestId(UUID.randomUUID().toString());
        tenantGateway.save(tenant);

        platformAuditLog.logTenantAction(
                operator,
                ACTION,
                tenantKey,
                AuditOutcomes.SUCCESS,
                "created status=PENDING plan=" + tenant.getPlan());
        log.info(
                "Platform tenant created (pending provisioning): {} (id={}, requestId={})",
                tenantKey,
                tenant.getTenantId(),
                tenant.getProvisionRequestId());
        return TenantVoConverter.INSTANCE.toVO(tenant);
    }
}
