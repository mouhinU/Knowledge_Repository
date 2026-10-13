package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.service.ProvisioningOutcome.InviteInfo;
import com.mouhin.knowledge.repository.application.service.ProvisioningOutcome.StepOutcome;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.gateway.TenantProvisionStepGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.entity.TenantProvisionStep;
import com.mouhin.knowledge.repository.domain.provisioning.ProvisioningContext;
import com.mouhin.knowledge.repository.domain.provisioning.ProvisioningStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 租户开通状态机（P3-T2）。
 *
 * <p>把"开通一个租户"编排成 {@code PENDING → PROVISIONING → ACTIVE/FAILED} 的可幂等重放流程：
 *
 * <ol>
 *   <li>{@link Tenant#beginProvisioning(String)} 迁入 PROVISIONING 并落库；
 *   <li>按 {@link ProvisioningStep#order()} 顺序执行各步骤，每步先查 {@code findSuccess}—— 命中则记 SKIPPED
 *       跳过（重放幂等），否则 {@code attemptNo = findMaxAttemptNo + 1} 插入 RUNNING 行、执行、 回填 SUCCESS/FAILED；
 *   <li>任一步失败即 {@link Tenant#failProvisioning()} 置 FAILED、审计、短路返回（不再跑后续步骤）；
 *   <li>全部成功 {@link Tenant#completeProvisioning()} 置 ACTIVE、审计。
 * </ol>
 *
 * <p><b>事务边界</b>：本方法<b>不</b>包裹单个大事务——每步的 {@code insert/update} 与租户状态迁移各自独立提交， 这样"中断后重试"才能看到前次已
 * SUCCESS 的步骤并跳过（S-5=方案 A 步骤级幂等）。步骤实现自身须保证首次执行幂等。
 *
 * <p>本类是 app 层 {@code @Service}，只做编排分发（COLA 红线 #8）；具体副作用在各 {@link ProvisioningStep} 实现内。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Service
public class ProvisioningStateMachine {

    /**
     * 审计动作名。
     */
    public static final String ACTION = "TENANT_PROVISION";
    private static final Logger log = LoggerFactory.getLogger(ProvisioningStateMachine.class);
    private final TenantGateway tenantGateway;
    private final TenantProvisionStepGateway stepGateway;
    private final PlatformAuditLog auditLog;
    private final List<ProvisioningStep> steps;

    public ProvisioningStateMachine(
            TenantGateway tenantGateway,
            TenantProvisionStepGateway stepGateway,
            PlatformAuditLog auditLog,
            List<ProvisioningStep> steps) {
        this.tenantGateway = tenantGateway;
        this.stepGateway = stepGateway;
        this.auditLog = auditLog;
        this.steps =
                steps.stream().sorted(Comparator.comparingInt(ProvisioningStep::order)).toList();
    }

    /**
     * 对指定租户执行开通编排。调用方须保证 {@code tenant} 处于可开通态（PENDING / PROVISIONING / FAILED）； ACTIVE
     * 租户直接幂等返回成功（不重跑步骤）。
     *
     * @param tenant             目标租户（已加载，含 tenantId）
     * @param provisionRequestId 本次请求 UUID v4
     * @param operatorId         操作者标识
     * @param operatorName       操作者显示名
     * @return 编排结果快照
     */
    public ProvisioningOutcome provision(
            Tenant tenant, String provisionRequestId, String operatorId, String operatorName) {

        // ACTIVE 幂等短路：已开通租户重复调用直接返回成功，不再跑步骤
        if (tenant.isActive()) {
            log.info(
                    "Tenant '{}' already ACTIVE, provisioning is a no-op (requestId={})",
                    tenant.getTenantKey(),
                    provisionRequestId);
            return new ProvisioningOutcome(provisionRequestId, Tenant.STATUS_ACTIVE, List.of());
        }

        tenant.beginProvisioning(provisionRequestId);
        tenantGateway.update(tenant);

        ProvisioningContext context =
                new ProvisioningContext(tenant, provisionRequestId, operatorId, operatorName);
        List<StepOutcome> outcomes = new ArrayList<>();

        for (ProvisioningStep step : steps) {
            StepOutcome outcome = runStep(tenant, provisionRequestId, step, context);
            outcomes.add(outcome);
            if (TenantProvisionStep.STATUS_FAILED.equals(outcome.status())) {
                tenant.failProvisioning();
                tenantGateway.update(tenant);
                auditLog.logTenantAction(
                        operatorId,
                        ACTION,
                        tenant.getTenantKey(),
                        "FAILED",
                        "step=" + step.name() + " error=" + outcome.errorDetail());
                return new ProvisioningOutcome(
                        provisionRequestId, Tenant.STATUS_FAILED, List.copyOf(outcomes));
            }
        }

        tenant.completeProvisioning();
        tenantGateway.update(tenant);
        auditLog.logTenantAction(
                operatorId, ACTION, tenant.getTenantKey(), "SUCCESS", "steps=" + outcomes.size());
        InviteInfo invite =
                (InviteInfo) context.attributes().get(ProvisioningContext.ATTR_INVITE_INFO);
        return new ProvisioningOutcome(
                provisionRequestId, Tenant.STATUS_ACTIVE, List.copyOf(outcomes), invite);
    }

    /**
     * 执行单步骤（含跳过判定与 RUNNING/SUCCESS/FAILED 行落库）。
     *
     * @return 步骤结果，永不为 {@code null}
     */
    private StepOutcome runStep(
            Tenant tenant,
            String provisionRequestId,
            ProvisioningStep step,
            ProvisioningContext ctx) {
        Long tenantId = tenant.getTenantId();
        String stepName = step.name();

        // 幂等：前次已 SUCCESS 则跳过，不新增行
        if (stepGateway.findSuccess(tenantId, provisionRequestId, stepName).isPresent()) {
            int skippedAttempt =
                    stepGateway.findMaxAttemptNo(tenantId, provisionRequestId, stepName);
            log.debug("Step '{}' already SUCCESS for tenant {}, skip", stepName, tenantId);
            return new StepOutcome(
                    stepName, TenantProvisionStep.STATUS_SKIPPED, skippedAttempt, null);
        }

        int attemptNo = stepGateway.findMaxAttemptNo(tenantId, provisionRequestId, stepName) + 1;
        TenantProvisionStep record = new TenantProvisionStep();
        record.setTenantId(tenantId);
        record.setProvisionRequestId(provisionRequestId);
        record.setStepName(stepName);
        record.setAttemptNo(attemptNo);
        record.setStatus(TenantProvisionStep.STATUS_RUNNING);
        record.setStartedAt(LocalDateTime.now());
        stepGateway.insert(record);

        try {
            step.execute(ctx);
            record.setStatus(TenantProvisionStep.STATUS_SUCCESS);
            record.setFinishedAt(LocalDateTime.now());
            stepGateway.update(record);
            return new StepOutcome(stepName, TenantProvisionStep.STATUS_SUCCESS, attemptNo, null);
        } catch (RuntimeException ex) {
            String detail = TenantProvisionStep.truncateErrorDetail(describe(ex));
            record.setStatus(TenantProvisionStep.STATUS_FAILED);
            record.setErrorDetail(detail);
            record.setFinishedAt(LocalDateTime.now());
            stepGateway.update(record);
            log.warn(
                    "Step '{}' FAILED for tenant {} (attempt {}): {}",
                    stepName,
                    tenant.getTenantKey(),
                    attemptNo,
                    detail);
            return new StepOutcome(stepName, TenantProvisionStep.STATUS_FAILED, attemptNo, detail);
        }
    }

    /**
     * 组装异常摘要：类型 + message，避免 NPE 时无信息。
     */
    private String describe(RuntimeException ex) {
        String msg = ex.getMessage();
        return ex.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }
}
