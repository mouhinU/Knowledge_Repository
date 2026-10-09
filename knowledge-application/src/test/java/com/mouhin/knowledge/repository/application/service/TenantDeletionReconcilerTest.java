package com.mouhin.knowledge.repository.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantDataPurgeGateway;
import com.mouhin.knowledge.repository.domain.gateway.TenantDeletionStepGateway;
import com.mouhin.knowledge.repository.domain.gateway.TenantDirectory;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.entity.TenantDeletionStep;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TenantDeletionReconciler} 删除对账器单测（P3-T5，S-4=方案 A 硬删引擎）。
 *
 * <p>锁定：两步皆成功 → DELETING→DELETED + 缓存失效 + 审计 SUCCESS；DB 步失败 → 保留 DELETING + 审计 FAILED + 不跑向量步 +
 * 不迁终态； 前次已 SUCCESS 的步骤续跑时 SKIPPED（不重复清除）。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@DisplayName("租户删除对账器 (TenantDeletionReconciler)")
class TenantDeletionReconcilerTest {

    private final TenantGateway tenantGateway = mock(TenantGateway.class);
    private final TenantDeletionStepGateway stepGateway = mock(TenantDeletionStepGateway.class);
    private final TenantDataPurgeGateway purgeGateway = mock(TenantDataPurgeGateway.class);
    private final VectorStoreGateway vectorStoreGateway = mock(VectorStoreGateway.class);
    private final PlatformAuditLog auditLog = mock(PlatformAuditLog.class);
    private final TenantDirectory tenantDirectory = mock(TenantDirectory.class);

    private final TenantDeletionReconciler reconciler =
            new TenantDeletionReconciler(
                    tenantGateway,
                    stepGateway,
                    purgeGateway,
                    vectorStoreGateway,
                    auditLog,
                    tenantDirectory);

    private Tenant deletingTenant() {
        Tenant t = new Tenant();
        t.setTenantId(1L);
        t.setTenantKey("acme");
        t.setStatus(Tenant.STATUS_DELETING);
        t.setDeletionRequestId("req-1");
        return t;
    }

    private Map<String, Long> rows() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("kb_document", 3L);
        m.put("sys_user", 2L);
        return m;
    }

    @Test
    @DisplayName("两步皆成功 → DELETING→DELETED + 落库 + 缓存失效 + 审计 SUCCESS")
    void bothStepsSucceed() {
        Tenant tenant = deletingTenant();
        when(purgeGateway.purgeTenantRows(1L)).thenReturn(rows());

        reconciler.runDeletion(tenant);

        assertThat(tenant.getStatus()).isEqualTo(Tenant.STATUS_DELETED);
        assertThat(tenant.getDeletedAt()).isNotNull();
        verify(vectorStoreGateway).deleteByTenantId(1L);
        verify(tenantGateway).update(tenant);
        verify(tenantDirectory).invalidateByKey("acme");
        // 两步各 insert RUNNING + update 终态
        verify(stepGateway, times(2)).insert(any(TenantDeletionStep.class));
        verify(stepGateway, times(2)).update(any(TenantDeletionStep.class));
        verify(auditLog)
                .logTenantAction(
                        eq("system"),
                        eq(TenantDeletionReconciler.ACTION),
                        eq("acme"),
                        eq("SUCCESS"),
                        any());
    }

    @Test
    @DisplayName("DB 步失败 → 保留 DELETING + 审计 FAILED + 不跑向量步 + 不迁终态")
    void dbStepFailureShortCircuits() {
        Tenant tenant = deletingTenant();
        when(purgeGateway.purgeTenantRows(1L)).thenThrow(new RuntimeException("db down"));

        reconciler.runDeletion(tenant);

        assertThat(tenant.getStatus()).isEqualTo(Tenant.STATUS_DELETING);
        verify(vectorStoreGateway, never()).deleteByTenantId(anyLong());
        verify(tenantGateway, never()).update(any());
        verify(tenantDirectory, never()).invalidateByKey(any());
        verify(auditLog)
                .logTenantAction(
                        eq("system"),
                        eq(TenantDeletionReconciler.ACTION),
                        eq("acme"),
                        eq("FAILED"),
                        any());
    }

    @Test
    @DisplayName("续跑：DB 步前次已 SUCCESS → SKIPPED（不重复清除），仅跑向量步 → DELETED")
    void dbStepSkippedWhenAlreadySuccess() {
        Tenant tenant = deletingTenant();
        TenantDeletionStep prior = new TenantDeletionStep();
        prior.setStatus(TenantDeletionStep.STATUS_SUCCESS);
        when(stepGateway.findSuccess(1L, "req-1", TenantDeletionReconciler.STEP_PURGE_DB_ROWS))
                .thenReturn(Optional.of(prior));
        when(stepGateway.findMaxAttemptNo(1L, "req-1", TenantDeletionReconciler.STEP_PURGE_DB_ROWS))
                .thenReturn(1);

        reconciler.runDeletion(tenant);

        assertThat(tenant.getStatus()).isEqualTo(Tenant.STATUS_DELETED);
        verify(purgeGateway, never()).purgeTenantRows(anyLong());
        verify(vectorStoreGateway).deleteByTenantId(1L);
        // 仅向量步新增一行
        verify(stepGateway, times(1)).insert(any(TenantDeletionStep.class));
    }

    @Test
    @DisplayName("DELETING 但缺 deletionRequestId → 跳过（数据修复），不清除不迁终态")
    void missingRequestIdSkipped() {
        Tenant tenant = deletingTenant();
        tenant.setDeletionRequestId(null);

        reconciler.runDeletion(tenant);

        assertThat(tenant.getStatus()).isEqualTo(Tenant.STATUS_DELETING);
        verify(purgeGateway, never()).purgeTenantRows(anyLong());
        verify(vectorStoreGateway, never()).deleteByTenantId(anyLong());
        verify(tenantGateway, never()).update(any());
    }

    @Test
    @DisplayName("reconcile: 扫描 DELETING 批量并逐个执行；单租户异常不影响其余")
    void reconcileScansAndIsolatesFailures() {
        Tenant bad = deletingTenant();
        bad.setTenantKey("bad");
        Tenant good = deletingTenant();
        good.setTenantId(2L);
        good.setTenantKey("good");
        when(tenantGateway.listByStatus(
                        eq(Tenant.STATUS_DELETING), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(bad, good));
        when(purgeGateway.purgeTenantRows(1L)).thenThrow(new RuntimeException("boom"));
        when(purgeGateway.purgeTenantRows(2L)).thenReturn(rows());

        reconciler.reconcile();

        // good 仍被处理到终态
        assertThat(good.getStatus()).isEqualTo(Tenant.STATUS_DELETED);
        assertThat(bad.getStatus()).isEqualTo(Tenant.STATUS_DELETING);
    }
}
