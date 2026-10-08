package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.application.converter.TenantVoConverter;
import com.mouhin.knowledge.repository.client.dto.TenantStatusCmd;
import com.mouhin.knowledge.repository.client.dto.TenantVO;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台端软停租户命令执行器（app 层用例，事务边界，P3-T1）。
 *
 * <p>把目标租户从 {@link Tenant#STATUS_ACTIVE} 迁到 {@link Tenant#STATUS_SUSPENDED}；状态流转合法性由 domain 实体
 * {@link Tenant#suspend()} 内聚判定（非 ACTIVE 抛 {@link IllegalStateException}）。软停后租户侧请求返 403 的运行时拦截由
 * P3-T4 落地，本执行器只负责状态落库 + 审计。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Component
@Slf4j
public class TenantSuspendCmdExe {

    /** 审计动作名。 */
    public static final String ACTION = "TENANT_SUSPEND";

    private final TenantGateway tenantGateway;
    private final PlatformAuditLog platformAuditLog;

    public TenantSuspendCmdExe(TenantGateway tenantGateway, PlatformAuditLog platformAuditLog) {
        this.tenantGateway = tenantGateway;
        this.platformAuditLog = platformAuditLog;
    }

    @Transactional
    public TenantVO execute(TenantStatusCmd cmd) {
        String operator = cmd.getOperatorId();
        String tenantKey = TenantKey.normalize(cmd.getTenantKey());
        Tenant tenant =
                tenantGateway
                        .findByTenantKey(tenantKey)
                        .orElseThrow(
                                () -> {
                                    platformAuditLog.logTenantAction(
                                            operator,
                                            ACTION,
                                            tenantKey,
                                            "REJECTED",
                                            "tenant not found");
                                    return new IllegalArgumentException(
                                            "Tenant not found: " + tenantKey);
                                });
        try {
            tenant.suspend();
        } catch (IllegalStateException e) {
            platformAuditLog.logTenantAction(
                    operator, ACTION, tenantKey, "REJECTED", e.getMessage());
            throw e;
        }
        tenantGateway.update(tenant);

        platformAuditLog.logTenantAction(
                operator,
                ACTION,
                tenantKey,
                "SUCCESS",
                "ACTIVE→SUSPENDED reason=" + cmd.getReason());
        log.info("Platform tenant suspended: {}", tenantKey);
        return TenantVoConverter.toVO(tenant);
    }
}
