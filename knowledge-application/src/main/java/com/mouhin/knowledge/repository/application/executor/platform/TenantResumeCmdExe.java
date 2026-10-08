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
 * 平台端恢复租户命令执行器（app 层用例，事务边界，P3-T1）。
 *
 * <p>把目标租户从 {@link Tenant#STATUS_SUSPENDED} 迁回 {@link Tenant#STATUS_ACTIVE}；合法性由 domain 实体 {@link
 * Tenant#resume()} 内聚判定（非 SUSPENDED 抛 {@link IllegalStateException}）。恢复后 {@code TenantIdResolver}
 * 缓存的重新预热由 P3-T4 的钩子处理（软停时失效、恢复时下次解析回源），本执行器只负责状态落库 + 审计。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Component
@Slf4j
public class TenantResumeCmdExe {

    /** 审计动作名。 */
    public static final String ACTION = "TENANT_RESUME";

    private final TenantGateway tenantGateway;
    private final PlatformAuditLog platformAuditLog;

    public TenantResumeCmdExe(TenantGateway tenantGateway, PlatformAuditLog platformAuditLog) {
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
            tenant.resume();
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
                "SUSPENDED→ACTIVE reason=" + cmd.getReason());
        log.info("Platform tenant resumed: {}", tenantKey);
        return TenantVoConverter.toVO(tenant);
    }
}
