package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.application.support.SystemConfigService;
import com.mouhin.knowledge.repository.client.dto.TenantArchiveCmd;
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
 * {@link ArchiveTenantCmdExe} 归档执行器单测（P3-T5，S-4=方案 A 保留期起点）。
 *
 * <p>锁定：ACTIVE→ARCHIVED / SUSPENDED→ARCHIVED 正常流转 + 打 archivedAt + 落库 + 缓存失效 + 审计 SUCCESS；
 * 非法态迁移与租户不存在拒绝 + 审计 REJECTED + 不落库不失效缓存。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@DisplayName("平台端归档执行器 (ArchiveTenantCmdExe)")
class ArchiveTenantCmdExeTest {

    private final TenantGateway tenantGateway = mock(TenantGateway.class);
    private final PlatformAuditLog auditLog = mock(PlatformAuditLog.class);
    private final TenantDirectory tenantDirectory = mock(TenantDirectory.class);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final ArchiveTenantCmdExe exe =
            new ArchiveTenantCmdExe(tenantGateway, auditLog, tenantDirectory, systemConfigService);

    private TenantArchiveCmd cmd(String key) {
        TenantArchiveCmd c = new TenantArchiveCmd();
        c.setTenantKey(key);
        c.setReason("offboarding");
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
    @DisplayName("archive: ACTIVE → ARCHIVED，打 archivedAt + 落库 + 缓存失效 + 审计 SUCCESS")
    void archiveActive() {
        when(tenantGateway.findByTenantKey("acme"))
                .thenReturn(Optional.of(tenant(Tenant.STATUS_ACTIVE)));

        TenantVO vo = exe.execute(cmd("acme"));

        assertThat(vo.getStatus()).isEqualTo(Tenant.STATUS_ARCHIVED);
        assertThat(vo.getArchivedAt()).isNotNull();
        verify(tenantGateway).update(any(Tenant.class));
        verify(tenantDirectory).invalidateByKey("acme");
        verify(systemConfigService).evictTenant("acme");
        verify(auditLog)
                .logTenantAction(
                        eq("op-1"),
                        eq(ArchiveTenantCmdExe.ACTION),
                        eq("acme"),
                        eq("SUCCESS"),
                        any());
    }

    @Test
    @DisplayName("archive: SUSPENDED → ARCHIVED 亦放行")
    void archiveSuspended() {
        when(tenantGateway.findByTenantKey("acme"))
                .thenReturn(Optional.of(tenant(Tenant.STATUS_SUSPENDED)));

        TenantVO vo = exe.execute(cmd("acme"));

        assertThat(vo.getStatus()).isEqualTo(Tenant.STATUS_ARCHIVED);
        verify(tenantGateway).update(any(Tenant.class));
    }

    @Test
    @DisplayName("archive: 非法态（PENDING）→ 抛 IllegalStateException、不落库、审计 REJECTED、不失效缓存")
    void archiveNonArchivableRejected() {
        when(tenantGateway.findByTenantKey("acme"))
                .thenReturn(Optional.of(tenant(Tenant.STATUS_PENDING)));

        assertThatThrownBy(() -> exe.execute(cmd("acme")))
                .isInstanceOf(IllegalStateException.class);

        verify(tenantGateway, never()).update(any());
        verify(tenantDirectory, never()).invalidateByKey(any());
        verify(systemConfigService, never()).evictTenant(any());
        verify(auditLog)
                .logTenantAction(
                        eq("op-1"),
                        eq(ArchiveTenantCmdExe.ACTION),
                        eq("acme"),
                        eq("REJECTED"),
                        any());
    }

    @Test
    @DisplayName("租户不存在 → 抛 IllegalArgumentException、审计 REJECTED")
    void tenantNotFound() {
        when(tenantGateway.findByTenantKey("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(cmd("ghost")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");

        verify(auditLog)
                .logTenantAction(
                        eq("op-1"),
                        eq(ArchiveTenantCmdExe.ACTION),
                        eq("ghost"),
                        eq("REJECTED"),
                        any());
        verify(tenantDirectory, never()).invalidateByKey(any());
    }
}
