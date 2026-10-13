package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.application.converter.TenantVoConverter;
import com.mouhin.knowledge.repository.application.support.SystemConfigService;
import com.mouhin.knowledge.repository.client.dto.TenantStatusCmd;
import com.mouhin.knowledge.repository.client.dto.TenantVO;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantDirectory;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台端恢复租户命令执行器（app 层用例，事务边界，P3-T1；P3-T4 补缓存即时失效）。
 *
 * <p>把目标租户从 {@link Tenant#STATUS_SUSPENDED} 迁回 {@link Tenant#STATUS_ACTIVE}；合法性由 domain 实体 {@link
 * Tenant#resume()} 内聚判定（非 SUSPENDED 抛 {@link IllegalStateException}）。
 *
 * <p><b>P3-T4 即时生效</b>：状态落库后立即失效 {@link TenantDirectory} 缓存，请求路径软拦（{@code TenantResolverFilter}）
 * 下一次读取即回源看到 ACTIVE → 放行（不再 403）；同时 evict 该租户的 {@link SystemConfigService} 配置缓存， 恢复后首次请求重新加载最新配置。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Component
@Slf4j
public class TenantResumeCmdExe {

    /**
     * 审计动作名。
     */
    public static final String ACTION = "TENANT_RESUME";

    private final TenantGateway tenantGateway;
    private final PlatformAuditLog platformAuditLog;
    private final TenantDirectory tenantDirectory;
    private final SystemConfigService systemConfigService;

    public TenantResumeCmdExe(
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

        // P3-T4 即时生效：失效租户目录缓存（下次请求路径软拦回源即见 ACTIVE → 放行）+ evict 配置缓存
        tenantDirectory.invalidateByKey(tenantKey);
        systemConfigService.evictTenant(tenantKey);

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
