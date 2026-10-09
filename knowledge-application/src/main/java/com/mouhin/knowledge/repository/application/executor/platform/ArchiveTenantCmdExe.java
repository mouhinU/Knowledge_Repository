package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.application.converter.TenantVoConverter;
import com.mouhin.knowledge.repository.application.support.SystemConfigService;
import com.mouhin.knowledge.repository.client.dto.TenantArchiveCmd;
import com.mouhin.knowledge.repository.client.dto.TenantVO;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantDirectory;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantKey;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台端归档租户命令执行器（app 层用例，事务边界，P3-T5；S-4=方案 A 保留期起点）。
 *
 * <p>把目标租户从 {@link Tenant#STATUS_ACTIVE} 或 {@link Tenant#STATUS_SUSPENDED} 迁到 {@link
 * Tenant#STATUS_ARCHIVED}， 并打 {@code archivedAt} 保留期起点戳；状态流转合法性由 domain 实体 {@link
 * Tenant#archive(LocalDateTime)} 内聚判定。 归档是软删——保留期内数据仍在，可经 {@code Tenant#restore()} 恢复；过保留期 +
 * 双人复核后才由 {@code TenantDeletionReconciler} 硬删。
 *
 * <p><b>即时生效</b>：与软停一致，落库后立即失效 {@link TenantDirectory} 缓存 + evict {@link SystemConfigService} 配置缓存，
 * 使请求路径软拦下一次读取即回源看到 ARCHIVED（非 ACTIVE → {@code TenantIdResolver.resolve} 抛异常 → 403）。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Component
@Slf4j
public class ArchiveTenantCmdExe {

    /** 审计动作名。 */
    public static final String ACTION = "TENANT_ARCHIVE";

    private final TenantGateway tenantGateway;
    private final PlatformAuditLog platformAuditLog;
    private final TenantDirectory tenantDirectory;
    private final SystemConfigService systemConfigService;

    public ArchiveTenantCmdExe(
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
    public TenantVO execute(TenantArchiveCmd cmd) {
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
        String fromStatus = tenant.getStatus();
        try {
            tenant.archive(LocalDateTime.now());
        } catch (IllegalStateException e) {
            platformAuditLog.logTenantAction(
                    operator, ACTION, tenantKey, "REJECTED", e.getMessage());
            throw e;
        }
        tenantGateway.update(tenant);

        // 即时生效：失效租户目录缓存（下次请求路径软拦回源即见 ARCHIVED）+ evict 配置缓存
        tenantDirectory.invalidateByKey(tenantKey);
        systemConfigService.evictTenant(tenantKey);

        platformAuditLog.logTenantAction(
                operator,
                ACTION,
                tenantKey,
                "SUCCESS",
                fromStatus + "→ARCHIVED reason=" + cmd.getReason());
        log.info("Platform tenant archived: {}", tenantKey);
        return TenantVoConverter.toVO(tenant);
    }
}
