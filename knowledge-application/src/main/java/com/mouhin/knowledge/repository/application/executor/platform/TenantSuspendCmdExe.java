package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.application.converter.TenantVoConverter;
import com.mouhin.knowledge.repository.application.support.SystemConfigService;
import com.mouhin.knowledge.repository.client.dto.TenantStatusCmd;
import com.mouhin.knowledge.repository.client.dto.TenantVO;
import com.mouhin.knowledge.repository.domain.constant.AuditOutcomes;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantDirectory;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台端软停租户命令执行器（app 层用例，事务边界，P3-T1；P3-T4 补缓存即时失效）。
 *
 * <p>把目标租户从 {@link Tenant#STATUS_ACTIVE} 迁到 {@link Tenant#STATUS_SUSPENDED}；状态流转合法性由 domain 实体
 * {@link Tenant#suspend()} 内聚判定（非 ACTIVE 抛 {@link IllegalStateException}）。
 *
 * <p><b>P3-T4 即时生效</b>：状态落库后立即失效 {@link TenantDirectory} 缓存（键↔id 映射 + 状态）， 使请求路径软拦（{@code
 * TenantResolverFilter}）下一次读取即回源看到 SUSPENDED → 403 {@code TENANT_SUSPENDED}； 同时 evict 该租户的 {@link
 * SystemConfigService} 配置缓存，停用期间不再持有其配置。 平台端 {@code /api/platform/**} 走独立平台令牌，不受租户软停影响。
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
    private final TenantDirectory tenantDirectory;
    private final SystemConfigService systemConfigService;

    public TenantSuspendCmdExe(
            TenantGateway tenantGateway,
            PlatformAuditLog platformAuditLog,
            TenantDirectory tenantDirectory,
            SystemConfigService systemConfigService) {
        this.tenantGateway = tenantGateway;
        this.platformAuditLog = platformAuditLog;
        this.tenantDirectory = tenantDirectory;
        this.systemConfigService = systemConfigService;
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
                                            AuditOutcomes.REJECTED,
                                            "tenant not found");
                                    return new IllegalArgumentException(
                                            "Tenant not found: " + tenantKey);
                                });
        try {
            tenant.suspend();
        } catch (IllegalStateException e) {
            platformAuditLog.logTenantAction(
                    operator, ACTION, tenantKey, AuditOutcomes.REJECTED, e.getMessage());
            throw e;
        }
        tenantGateway.update(tenant);

        // P3-T4 即时生效：失效租户目录缓存（下次请求路径软拦回源即见 SUSPENDED）+ evict 配置缓存
        tenantDirectory.invalidateByKey(tenantKey);
        systemConfigService.evictTenant(tenantKey);

        platformAuditLog.logTenantAction(
                operator,
                ACTION,
                tenantKey,
                AuditOutcomes.SUCCESS,
                "ACTIVE→SUSPENDED reason=" + cmd.getReason());
        log.info("Platform tenant suspended: {}", tenantKey);
        return TenantVoConverter.INSTANCE.toVO(tenant);
    }
}
