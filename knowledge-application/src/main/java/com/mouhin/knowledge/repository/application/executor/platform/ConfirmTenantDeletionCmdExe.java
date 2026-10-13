package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.application.converter.TenantVoConverter;
import com.mouhin.knowledge.repository.application.support.DeleteApprovalService;
import com.mouhin.knowledge.repository.application.support.SystemConfigService;
import com.mouhin.knowledge.repository.client.dto.TenantDeleteCmd;
import com.mouhin.knowledge.repository.client.dto.TenantVO;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantDirectory;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.entity.TenantDeleteApproval;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 平台端删除确认执行器（app 层用例，事务边界，P3-T5；S-4=方案 A 双人复核第二步）。
 *
 * <p>第二操作者持签发时一次性返回的 raw token 确认删除。放行前须<b>全部</b>满足：
 *
 * <ol>
 *   <li>租户存在且处于 {@link Tenant#STATUS_ARCHIVED}（只有归档态进入删除流程）；
 *   <li>保留期已过：{@code archivedAt + retentionDays <= now}（S-4=方案 A 的 30 天保留期）；
 *   <li>令牌有效：按 raw token 的 SHA-256 命中一条属于本租户、{@code PENDING} 且未过期的审批；
 *   <li>双人复核：确认者 {@code operatorId} ≠ 签发者 {@code approverOperatorId}（同一人签发并确认视为无效）。
 * </ol>
 *
 * <p>通过后：消费令牌（置 USED + 回填 usedAt/usedBy）→ {@link Tenant#markDeleting(String)} 迁入 DELETING 并绑定新 UUID
 * v4 {@code deletionRequestId} → 失效目录 / 配置缓存 → 审计 SUCCESS。真正的硬删由 {@code TenantDeletionReconciler}
 * 异步续跑， 本执行器<b>不</b>删任何业务数据（避免把长耗时破坏性 IO 放进请求事务，红线 #8）。
 *
 * <p><b>安全纪律</b>（红线 #4）：raw token 绝不写入审计 detail / 日志；所有拒绝原因只描述状态，不回显令牌。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Component
@Slf4j
public class ConfirmTenantDeletionCmdExe {

    /**
     * 审计动作名。
     */
    public static final String ACTION = "TENANT_DELETE_CONFIRM";

    private final TenantGateway tenantGateway;
    private final DeleteApprovalService deleteApprovalService;
    private final PlatformAuditLog platformAuditLog;
    private final TenantDirectory tenantDirectory;
    private final SystemConfigService systemConfigService;

    /**
     * S-4=方案 A 保留期天数（默认 30）。
     */
    @Value("${knowledge.saas.tenant-deletion.retention-days:30}")
    private int retentionDays;

    public ConfirmTenantDeletionCmdExe(
            TenantGateway tenantGateway,
            DeleteApprovalService deleteApprovalService,
            PlatformAuditLog platformAuditLog,
            TenantDirectory tenantDirectory,
            SystemConfigService systemConfigService) {
        this.tenantGateway = tenantGateway;
        this.deleteApprovalService = deleteApprovalService;
        this.platformAuditLog = platformAuditLog;
        this.tenantDirectory = tenantDirectory;
        this.systemConfigService = systemConfigService;
    }

    @Transactional
    public TenantVO execute(TenantDeleteCmd cmd) {
        String operator = cmd.getOperatorId();
        String tenantKey = TenantKey.normalize(cmd.getTenantKey());
        LocalDateTime now = LocalDateTime.now();

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

        // 门槛 1：必须已归档
        if (!tenant.isArchived()) {
            return reject(
                    operator,
                    tenantKey,
                    "tenant not ARCHIVED, status=" + tenant.getStatus(),
                    new IllegalStateException(
                            "Cannot delete tenant '"
                                    + tenantKey
                                    + "' from status="
                                    + tenant.getStatus()
                                    + " (must be ARCHIVED)"));
        }

        // 门槛 2：保留期已过
        LocalDateTime eligibleAt = eligibleAt(tenant, now);
        if (eligibleAt == null || now.isBefore(eligibleAt)) {
            return reject(
                    operator,
                    tenantKey,
                    "retention not elapsed, eligibleAt=" + eligibleAt,
                    new IllegalStateException(
                            "Tenant '"
                                    + tenantKey
                                    + "' retention period not elapsed (eligible at "
                                    + eligibleAt
                                    + ", retentionDays="
                                    + retentionDays
                                    + ")"));
        }

        // 门槛 3：令牌有效（命中 + 属于本租户 + PENDING + 未过期）
        Optional<TenantDeleteApproval> found =
                deleteApprovalService.findByRawToken(cmd.getApprovalToken());
        if (found.isEmpty()) {
            return reject(
                    operator,
                    tenantKey,
                    "approval token invalid",
                    new IllegalArgumentException("Delete approval token is invalid"));
        }
        TenantDeleteApproval approval = found.get();
        if (!Objects.equals(approval.getTenantId(), tenant.getTenantId())) {
            return reject(
                    operator,
                    tenantKey,
                    "approval token tenant mismatch",
                    new IllegalArgumentException("Delete approval token does not match tenant"));
        }
        if (!approval.isUsable(now)) {
            return reject(
                    operator,
                    tenantKey,
                    "approval not usable, status=" + approval.getStatus(),
                    new IllegalStateException(
                            "Delete approval is not usable (status="
                                    + approval.getStatus()
                                    + ", expired or already consumed)"));
        }

        // 门槛 4：双人复核——确认者 ≠ 签发者
        if (Objects.equals(operator, approval.getApproverOperatorId())) {
            return reject(
                    operator,
                    tenantKey,
                    "dual-review violation: confirmer == approver",
                    new IllegalStateException(
                            "Dual-review requires the confirmer to differ from the approver"));
        }

        // 放行：消费令牌 → 迁入 DELETING → 失效缓存 → 审计
        deleteApprovalService.consume(approval, now, operator);

        String deletionRequestId = UUID.randomUUID().toString();
        tenant.markDeleting(deletionRequestId);
        tenantGateway.update(tenant);

        tenantDirectory.invalidateByKey(tenantKey);
        systemConfigService.evictTenant(tenantKey);

        platformAuditLog.logTenantAction(
                operator,
                ACTION,
                tenantKey,
                "SUCCESS",
                "ARCHIVED→DELETING approvalId="
                        + approval.getApprovalId()
                        + " approver="
                        + approval.getApproverOperatorId()
                        + " deletionRequestId="
                        + deletionRequestId);
        log.info(
                "Tenant deletion confirmed (ARCHIVED→DELETING): {} deletionRequestId={}",
                tenantKey,
                deletionRequestId);
        return TenantVoConverter.toVO(tenant);
    }

    /**
     * 计算保留期截止点：{@code archivedAt + retentionDays}；未打归档戳返回 {@code null}。
     */
    private LocalDateTime eligibleAt(Tenant tenant, LocalDateTime now) {
        LocalDateTime archivedAt = tenant.getArchivedAt();
        if (archivedAt == null) {
            return null;
        }
        return archivedAt.plusDays(Math.max(0, retentionDays));
    }

    /**
     * 统一拒绝路径：审计 REJECTED + 抛出业务异常。
     */
    private TenantVO reject(String operator, String tenantKey, String detail, RuntimeException ex) {
        platformAuditLog.logTenantAction(operator, ACTION, tenantKey, "REJECTED", detail);
        throw ex;
    }
}
