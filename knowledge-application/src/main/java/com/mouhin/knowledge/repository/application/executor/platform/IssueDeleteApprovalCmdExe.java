package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.application.support.DeleteApprovalService;
import com.mouhin.knowledge.repository.application.support.DeleteApprovalService.ApprovalIssueResult;
import com.mouhin.knowledge.repository.client.dto.TenantDeleteApprovalCmd;
import com.mouhin.knowledge.repository.client.dto.TenantDeleteApprovalVO;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.entity.TenantDeleteApproval;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantKey;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台端删除审批令牌签发执行器（app 层用例，事务边界，P3-T5；S-4=方案 A 双人复核第一步）。
 *
 * <p>项目负责人为<b>已归档</b>租户签发一次性删除审批令牌：校验租户存在且处于 ARCHIVED（只有归档态才进入删除流程）， 经 {@link
 * DeleteApprovalService} 生成 raw token（仅此一次可见）+ 落库 hash，审计 SUCCESS 后返回含 raw token 的视图。
 *
 * <p><b>不做</b>保留期是否已过的校验——那是第二操作者{@code ConfirmTenantDeletionCmdExe} 消费令牌时的门槛；
 * 签发只要求"已归档"，允许项目负责人提前签发、待保留期到后再确认。
 *
 * <p><b>安全纪律</b>（红线 #4）：raw token 只在本执行器返回体出现一次，绝不写入审计 detail / 日志。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Component
@Slf4j
public class IssueDeleteApprovalCmdExe {

    /** 审计动作名。 */
    public static final String ACTION = "TENANT_DELETE_APPROVAL_ISSUE";

    private final TenantGateway tenantGateway;
    private final DeleteApprovalService deleteApprovalService;
    private final PlatformAuditLog platformAuditLog;

    public IssueDeleteApprovalCmdExe(
            TenantGateway tenantGateway,
            DeleteApprovalService deleteApprovalService,
            PlatformAuditLog platformAuditLog) {
        this.tenantGateway = tenantGateway;
        this.deleteApprovalService = deleteApprovalService;
        this.platformAuditLog = platformAuditLog;
    }

    @Transactional
    public TenantDeleteApprovalVO execute(TenantDeleteApprovalCmd cmd) {
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

        if (!tenant.isArchived()) {
            platformAuditLog.logTenantAction(
                    operator,
                    ACTION,
                    tenantKey,
                    "REJECTED",
                    "tenant not ARCHIVED, status=" + tenant.getStatus());
            throw new IllegalStateException(
                    "Cannot issue delete approval for tenant '"
                            + tenantKey
                            + "' from status="
                            + tenant.getStatus()
                            + " (must be ARCHIVED)");
        }

        Duration ttl =
                (cmd.getTtlHours() == null || cmd.getTtlHours() <= 0)
                        ? DeleteApprovalService.DEFAULT_TTL
                        : Duration.ofHours(cmd.getTtlHours());
        ApprovalIssueResult result =
                deleteApprovalService.issue(tenant.getTenantId(), operator, ttl);

        platformAuditLog.logTenantAction(
                operator,
                ACTION,
                tenantKey,
                "SUCCESS",
                "approvalId=" + result.approval().getApprovalId() + " ttlHours=" + ttl.toHours());
        log.info(
                "Delete approval issued for tenant {} (approvalId={})",
                tenantKey,
                result.approval().getApprovalId());
        return toVO(tenantKey, result);
    }

    private TenantDeleteApprovalVO toVO(String tenantKey, ApprovalIssueResult result) {
        TenantDeleteApproval approval = result.approval();
        TenantDeleteApprovalVO vo = new TenantDeleteApprovalVO();
        vo.setApprovalId(approval.getApprovalId());
        vo.setTenantKey(tenantKey);
        vo.setApproverOperatorId(approval.getApproverOperatorId());
        vo.setStatus(approval.getStatus());
        vo.setExpiresAt(approval.getExpiresAt());
        vo.setApprovalToken(result.rawToken());
        return vo;
    }
}
