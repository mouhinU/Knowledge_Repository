package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.application.converter.TenantProvisionVoConverter;
import com.mouhin.knowledge.repository.application.service.ProvisioningOutcome;
import com.mouhin.knowledge.repository.application.service.ProvisioningStateMachine;
import com.mouhin.knowledge.repository.client.dto.TenantProvisionCmd;
import com.mouhin.knowledge.repository.client.dto.TenantProvisionVO;
import com.mouhin.knowledge.repository.domain.constant.AuditOutcomes;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantKey;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 租户开通命令执行器（app 层用例，P3-T2）。
 *
 * <p>解析目标租户 → 决定本次 {@code provisionRequestId}（调用方传入优先，否则复用租户库中已有 id，再否则新造 UUID v4） → 委派 {@link
 * ProvisioningStateMachine} 编排 → 转 VO。审计动作 {@value #ACTION}。
 *
 * <p><b>不</b>加 {@code @Transactional}：状态机内部按步骤独立提交以支持"中断后重试跳过已成功步骤"（S-5=方案 A），
 * 外层大事务会破坏该幂等语义。租户元数据加载为只读，无写库事务需求。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Component
@Slf4j
public class TenantProvisionCmdExe {

    /** 审计动作名。 */
    public static final String ACTION = "TENANT_PROVISION";

    private final TenantGateway tenantGateway;
    private final ProvisioningStateMachine stateMachine;
    private final PlatformAuditLog platformAuditLog;

    public TenantProvisionCmdExe(
            TenantGateway tenantGateway,
            ProvisioningStateMachine stateMachine,
            PlatformAuditLog platformAuditLog) {
        this.tenantGateway = tenantGateway;
        this.stateMachine = stateMachine;
        this.platformAuditLog = platformAuditLog;
    }

    /**
     * 执行开通编排。
     *
     * @param cmd 开通命令
     * @return 开通结果 VO
     * @throws IllegalArgumentException 租户不存在
     * @throws IllegalStateException 租户处于不可开通态（ACTIVE 幂等返回、SUSPENDED/ARCHIVED/DELETED 拒绝）
     */
    public TenantProvisionVO execute(TenantProvisionCmd cmd) {
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

        String requestId = resolveRequestId(cmd.getProvisionRequestId(), tenant);

        ProvisioningOutcome outcome =
                stateMachine.provision(
                        tenant, requestId, cmd.getOperatorId(), cmd.getOperatorName());

        platformAuditLog.logTenantAction(
                operator,
                ACTION,
                tenantKey,
                outcome.isSuccess() ? AuditOutcomes.SUCCESS : AuditOutcomes.FAILED,
                "finalStatus=" + outcome.finalStatus() + " steps=" + outcome.steps().size());
        log.info(
                "Provisioning finished for tenant '{}': status={} requestId={}",
                tenantKey,
                outcome.finalStatus(),
                requestId);
        return TenantProvisionVoConverter.toVO(tenantKey, outcome);
    }

    /**
     * 决定本次请求 id：显式传入优先 → 租户库中已有 id → 新造 UUID v4。
     *
     * @param requested 调用方传入（可为 {@code null}/空白）
     * @param tenant 目标租户
     * @return 非空 UUID v4 字符串
     */
    private String resolveRequestId(String requested, Tenant tenant) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        if (tenant.getProvisionRequestId() != null && !tenant.getProvisionRequestId().isBlank()) {
            return tenant.getProvisionRequestId();
        }
        return UUID.randomUUID().toString();
    }
}
