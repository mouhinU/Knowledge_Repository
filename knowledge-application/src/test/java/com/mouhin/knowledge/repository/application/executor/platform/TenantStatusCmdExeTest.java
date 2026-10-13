package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.application.support.SystemConfigService;
import com.mouhin.knowledge.repository.client.dto.TenantStatusCmd;
import com.mouhin.knowledge.repository.client.dto.TenantVO;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantDirectory;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link TenantSuspendCmdExe} / {@link TenantResumeCmdExe} 软停与恢复执行器单测（P3-T1 + P3-T4 缓存即时失效）。
 *
 * <p>锁定：ACTIVE→SUSPENDED、SUSPENDED→ACTIVE 正常流转 + 落库 + 审计 SUCCESS；非法态迁移与租户不存在拒绝 + 审计 REJECTED + 不落库。
 *
 * <p>P3-T4：成功流转后须失效 {@link TenantDirectory} 缓存 + evict {@link SystemConfigService}
 * 配置缓存；拒绝路径两者均不得触发。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@DisplayName("平台端软停/恢复执行器 (TenantSuspend/ResumeCmdExe)")
class TenantStatusCmdExeTest {

    private final TenantGateway tenantGateway = mock(TenantGateway.class);
    private final PlatformAuditLog auditLog = mock(PlatformAuditLog.class);
    private final TenantDirectory tenantDirectory = mock(TenantDirectory.class);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final TenantSuspendCmdExe suspendExe =
            new TenantSuspendCmdExe(tenantGateway, auditLog, tenantDirectory, systemConfigService);
    private final TenantResumeCmdExe resumeExe =
            new TenantResumeCmdExe(tenantGateway, auditLog, tenantDirectory, systemConfigService);

    private TenantStatusCmd cmd(String key) {
        TenantStatusCmd c = new TenantStatusCmd();
        c.setTenantKey(key);
        c.setReason("test");
        c.setOperatorId("op-1");
        c.setOperatorName("platform-admin");
        return c;
    }

    private Tenant tenant(String status) {
        Tenant t = new Tenant();
        t.setTenantId(1L);
        t.setTenantKey("acme");
        t.setStatus(status);
        return t;
    }

    @Test
    @DisplayName("suspend: ACTIVE → SUSPENDED，落库 + 审计 SUCCESS")
    void suspendActive() {
        when(tenantGateway.findByTenantKey("acme"))
                .thenReturn(Optional.of(tenant(Tenant.STATUS_ACTIVE)));

        TenantVO vo = suspendExe.execute(cmd("acme"));

        assertThat(vo.getStatus()).isEqualTo(Tenant.STATUS_SUSPENDED);
        verify(tenantGateway).update(any(Tenant.class));
        verify(auditLog)
                .logTenantAction(
                        eq("op-1"),
                        eq(TenantSuspendCmdExe.ACTION),
                        eq("acme"),
                        eq("SUCCESS"),
                        any());
        verify(tenantDirectory).invalidateByKey("acme");
        verify(systemConfigService).evictTenant("acme");
    }

    @Test
    @DisplayName("suspend: 非 ACTIVE 态 → 抛 IllegalStateException、不落库、审计 REJECTED")
    void suspendNonActiveRejected() {
        when(tenantGateway.findByTenantKey("acme"))
                .thenReturn(Optional.of(tenant(Tenant.STATUS_SUSPENDED)));

        assertThatThrownBy(() -> suspendExe.execute(cmd("acme")))
                .isInstanceOf(IllegalStateException.class);

        verify(tenantGateway, never()).update(any());
        verify(auditLog)
                .logTenantAction(
                        eq("op-1"),
                        eq(TenantSuspendCmdExe.ACTION),
                        eq("acme"),
                        eq("REJECTED"),
                        any());
        verify(tenantDirectory, never()).invalidateByKey(any());
        verify(systemConfigService, never()).evictTenant(any());
    }

    @Test
    @DisplayName("resume: SUSPENDED → ACTIVE，落库 + 审计 SUCCESS")
    void resumeSuspended() {
        when(tenantGateway.findByTenantKey("acme"))
                .thenReturn(Optional.of(tenant(Tenant.STATUS_SUSPENDED)));

        TenantVO vo = resumeExe.execute(cmd("acme"));

        assertThat(vo.getStatus()).isEqualTo(Tenant.STATUS_ACTIVE);
        verify(tenantGateway).update(any(Tenant.class));
        verify(auditLog)
                .logTenantAction(
                        eq("op-1"),
                        eq(TenantResumeCmdExe.ACTION),
                        eq("acme"),
                        eq("SUCCESS"),
                        any());
        verify(tenantDirectory).invalidateByKey("acme");
        verify(systemConfigService).evictTenant("acme");
    }

    @Test
    @DisplayName("租户不存在 → 抛 IllegalArgumentException、审计 REJECTED")
    void tenantNotFound() {
        when(tenantGateway.findByTenantKey("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> suspendExe.execute(cmd("ghost")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");

        verify(auditLog)
                .logTenantAction(
                        eq("op-1"),
                        eq(TenantSuspendCmdExe.ACTION),
                        eq("ghost"),
                        eq("REJECTED"),
                        any());
        verify(tenantDirectory, never()).invalidateByKey(any());
        verify(systemConfigService, never()).evictTenant(any());
    }
}
