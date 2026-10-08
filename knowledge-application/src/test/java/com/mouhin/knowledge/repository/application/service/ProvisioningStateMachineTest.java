package com.mouhin.knowledge.repository.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.application.service.ProvisioningOutcome.StepOutcome;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.gateway.TenantProvisionStepGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.entity.TenantProvisionStep;
import com.mouhin.knowledge.repository.domain.provisioning.ProvisioningContext;
import com.mouhin.knowledge.repository.domain.provisioning.ProvisioningStep;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ProvisioningStateMachine} 编排单测（P3-T2）。
 *
 * <p>用 mock 网关 + 桩步骤锁定状态机核心不变式：全成功 → ACTIVE、任一步失败 → FAILED 且短路后续、重放时已 SUCCESS 步骤跳过（SKIPPED、
 * 不再执行）、attemptNo 递增、ACTIVE 租户幂等空跑。对应清单 §7 item 4 集成口径的单元级证据（"中断后重试不重复 / FAILED 可查原因 / 步骤级幂等"）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@DisplayName("租户开通状态机 (ProvisioningStateMachine) P3-T2")
class ProvisioningStateMachineTest {

    private TenantGateway tenantGateway;
    private TenantProvisionStepGateway stepGateway;
    private PlatformAuditLog auditLog;

    /** 记录步骤是否被真正执行（用于断言跳过语义）。 */
    private int stepAExecutions;

    private int stepBExecutions;

    @BeforeEach
    void setUp() {
        tenantGateway = mock(TenantGateway.class);
        stepGateway = mock(TenantProvisionStepGateway.class);
        auditLog = mock(PlatformAuditLog.class);
        stepAExecutions = 0;
        stepBExecutions = 0;
        // 默认：无任何历史成功步骤、attemptNo=0（首次）
        when(stepGateway.findSuccess(anyLong(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(stepGateway.findMaxAttemptNo(anyLong(), anyString(), anyString())).thenReturn(0);
    }

    private Tenant pendingTenant() {
        Tenant t = new Tenant();
        t.setTenantId(42L);
        t.setTenantKey("acme");
        t.setStatus(Tenant.STATUS_PENDING);
        return t;
    }

    private ProvisioningStep recordingStep(String name, int order, Runnable body) {
        return new ProvisioningStep() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public void execute(ProvisioningContext context) {
                body.run();
            }
        };
    }

    private ProvisioningStep failingStep(String name, int order, RuntimeException ex) {
        return new ProvisioningStep() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public void execute(ProvisioningContext context) {
                throw ex;
            }
        };
    }

    private ProvisioningStateMachine machine(ProvisioningStep... steps) {
        return new ProvisioningStateMachine(tenantGateway, stepGateway, auditLog, List.of(steps));
    }

    @Test
    @DisplayName("全部步骤成功 → 租户 ACTIVE，两步各执行一次，审计 SUCCESS")
    void allStepsSucceed() {
        ProvisioningStateMachine sm =
                machine(
                        recordingStep("STEP_A", 100, () -> stepAExecutions++),
                        recordingStep("STEP_B", 200, () -> stepBExecutions++));

        Tenant tenant = pendingTenant();
        ProvisioningOutcome outcome = sm.provision(tenant, "req-1", "op-1", "admin");

        assertThat(outcome.isSuccess()).isTrue();
        assertThat(outcome.finalStatus()).isEqualTo(Tenant.STATUS_ACTIVE);
        assertThat(tenant.isActive()).isTrue();
        assertThat(stepAExecutions).isEqualTo(1);
        assertThat(stepBExecutions).isEqualTo(1);
        assertThat(outcome.steps()).hasSize(2);
        assertThat(outcome.steps())
                .allSatisfy(
                        s -> assertThat(s.status()).isEqualTo(TenantProvisionStep.STATUS_SUCCESS));
        // beginProvisioning + completeProvisioning 各落一次库
        verify(tenantGateway, times(2)).update(tenant);
        verify(auditLog)
                .logTenantAction(
                        eq("op-1"),
                        eq(ProvisioningStateMachine.ACTION),
                        eq("acme"),
                        eq("SUCCESS"),
                        any());
    }

    @Test
    @DisplayName("步骤按 order 升序执行（构造时乱序传入亦被排序）")
    void stepsRunInOrderAscending() {
        StringBuilder trace = new StringBuilder();
        ProvisioningStateMachine sm =
                machine(
                        recordingStep("STEP_B", 200, () -> trace.append("B")),
                        recordingStep("STEP_A", 100, () -> trace.append("A")));

        sm.provision(pendingTenant(), "req-order", "op-1", "admin");

        assertThat(trace.toString()).isEqualTo("AB");
    }

    @Test
    @DisplayName("某步失败 → 租户 FAILED、短路后续步骤、失败步 error_detail 可查、审计 FAILED")
    void failingStepShortCircuits() {
        ProvisioningStateMachine sm =
                machine(
                        failingStep("STEP_A", 100, new RuntimeException("boom: Milvus down")),
                        recordingStep("STEP_B", 200, () -> stepBExecutions++));

        Tenant tenant = pendingTenant();
        ProvisioningOutcome outcome = sm.provision(tenant, "req-fail", "op-1", "admin");

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.finalStatus()).isEqualTo(Tenant.STATUS_FAILED);
        assertThat(tenant.isFailed()).isTrue();
        assertThat(stepBExecutions).isZero(); // 短路：失败后不再跑 STEP_B
        assertThat(outcome.steps()).hasSize(1);
        StepOutcome failed = outcome.steps().get(0);
        assertThat(failed.status()).isEqualTo(TenantProvisionStep.STATUS_FAILED);
        assertThat(failed.errorDetail()).contains("boom").contains("RuntimeException");
        verify(auditLog)
                .logTenantAction(
                        eq("op-1"),
                        eq(ProvisioningStateMachine.ACTION),
                        eq("acme"),
                        eq("FAILED"),
                        any());
    }

    @Test
    @DisplayName("重放：前次已 SUCCESS 的步骤跳过（SKIPPED、不再执行），仅跑未完成步骤")
    void replaySkipsAlreadySucceededSteps() {
        // STEP_A 前次已成功；STEP_B 无历史
        TenantProvisionStep priorSuccess = new TenantProvisionStep();
        priorSuccess.setStatus(TenantProvisionStep.STATUS_SUCCESS);
        when(stepGateway.findSuccess(eq(42L), eq("req-replay"), eq("STEP_A")))
                .thenReturn(Optional.of(priorSuccess));
        when(stepGateway.findMaxAttemptNo(eq(42L), eq("req-replay"), eq("STEP_A"))).thenReturn(1);

        ProvisioningStateMachine sm =
                machine(
                        recordingStep("STEP_A", 100, () -> stepAExecutions++),
                        recordingStep("STEP_B", 200, () -> stepBExecutions++));

        Tenant tenant = pendingTenant();
        ProvisioningOutcome outcome = sm.provision(tenant, "req-replay", "op-1", "admin");

        assertThat(outcome.isSuccess()).isTrue();
        assertThat(stepAExecutions).isZero(); // 已成功步骤不再执行（幂等）
        assertThat(stepBExecutions).isEqualTo(1);
        assertThat(outcome.steps()).hasSize(2);
        assertThat(outcome.steps().get(0).status()).isEqualTo(TenantProvisionStep.STATUS_SKIPPED);
        assertThat(outcome.steps().get(1).status()).isEqualTo(TenantProvisionStep.STATUS_SUCCESS);
    }

    @Test
    @DisplayName("失败重放：attemptNo = 历史最大 + 1（步骤级重放计数递增）")
    void replayIncrementsAttemptNo() {
        // 前次 STEP_A 已尝试到 attempt 2（但非 SUCCESS），本次应为 3
        when(stepGateway.findMaxAttemptNo(eq(42L), eq("req-retry"), eq("STEP_A"))).thenReturn(2);

        ProvisioningStateMachine sm =
                machine(recordingStep("STEP_A", 100, () -> stepAExecutions++));

        sm.provision(pendingTenant(), "req-retry", "op-1", "admin");

        // 捕获插入的步骤行，断言 attemptNo=3
        org.mockito.ArgumentCaptor<TenantProvisionStep> cap =
                org.mockito.ArgumentCaptor.forClass(TenantProvisionStep.class);
        verify(stepGateway).insert(cap.capture());
        assertThat(cap.getValue().getAttemptNo()).isEqualTo(3);
    }

    @Test
    @DisplayName("ACTIVE 租户重复开通 → 幂等空跑，不迁状态、不落步骤、不执行任何步骤")
    void activeTenantIsNoOp() {
        ProvisioningStateMachine sm =
                machine(recordingStep("STEP_A", 100, () -> stepAExecutions++));

        Tenant active = pendingTenant();
        active.setStatus(Tenant.STATUS_ACTIVE);
        ProvisioningOutcome outcome = sm.provision(active, "req-noop", "op-1", "admin");

        assertThat(outcome.isSuccess()).isTrue();
        assertThat(outcome.finalStatus()).isEqualTo(Tenant.STATUS_ACTIVE);
        assertThat(outcome.steps()).isEmpty();
        assertThat(stepAExecutions).isZero();
        verify(stepGateway, never()).insert(any());
        verify(tenantGateway, never()).update(any());
    }

    @Test
    @DisplayName("FAILED 态租户可重放：beginProvisioning 允许 FAILED → PROVISIONING")
    void failedTenantCanBeReprovisioned() {
        ProvisioningStateMachine sm =
                machine(recordingStep("STEP_A", 100, () -> stepAExecutions++));

        Tenant failed = pendingTenant();
        failed.setStatus(Tenant.STATUS_FAILED);
        ProvisioningOutcome outcome = sm.provision(failed, "req-replay-failed", "op-1", "admin");

        assertThat(outcome.isSuccess()).isTrue();
        assertThat(failed.isActive()).isTrue();
        assertThat(stepAExecutions).isEqualTo(1);
    }
}
