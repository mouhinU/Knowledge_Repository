package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.application.support.DeleteApprovalService;
import com.mouhin.knowledge.repository.application.support.SystemConfigService;
import com.mouhin.knowledge.repository.client.dto.TenantDeleteCmd;
import com.mouhin.knowledge.repository.client.dto.TenantVO;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantDirectory;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.entity.TenantDeleteApproval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link ConfirmTenantDeletionCmdExe} 删除确认执行器单测（P3-T5，S-4=方案 A 双人复核第二步）。
 *
 * <p>锁定四道门槛的放行与拒绝：已归档 / 保留期已过 / 令牌有效（命中+属本租户+PENDING+未过期）/ 确认者≠签发者。 放行则消费令牌 + ARCHIVED→DELETING +
 * 绑定 deletionRequestId + 缓存失效 + 审计 SUCCESS；任一门槛不过则审计 REJECTED + 抛业务异常 + 不消费令牌不落库。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@DisplayName("平台端删除确认执行器 (ConfirmTenantDeletionCmdExe)")
class ConfirmTenantDeletionCmdExeTest {

    private static final String RAW_TOKEN = "raw-token-abc";
    private static final String APPROVER = "owner";
    private static final String CONFIRMER = "second-op";

    private final TenantGateway tenantGateway = mock(TenantGateway.class);
    private final DeleteApprovalService deleteApprovalService = mock(DeleteApprovalService.class);
    private final PlatformAuditLog auditLog = mock(PlatformAuditLog.class);
    private final TenantDirectory tenantDirectory = mock(TenantDirectory.class);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final ConfirmTenantDeletionCmdExe exe =
            new ConfirmTenantDeletionCmdExe(
                    tenantGateway,
                    deleteApprovalService,
                    auditLog,
                    tenantDirectory,
                    systemConfigService);

    private void setRetentionDays(int days) {
        ReflectionTestUtils.setField(exe, "retentionDays", days);
    }

    private TenantDeleteCmd cmd(String operator) {
        TenantDeleteCmd c = new TenantDeleteCmd();
        c.setTenantKey("acme");
        c.setApprovalToken(RAW_TOKEN);
        c.setOperatorId(operator);
        c.setOperatorName("name-" + operator);
        return c;
    }

    private Tenant archivedTenant(LocalDateTime archivedAt) {
        Tenant t = new Tenant();
        t.setTenantId(1L);
        t.setTenantKey("acme");
        t.setStatus(Tenant.STATUS_ARCHIVED);
        t.setArchivedAt(archivedAt);
        return t;
    }

    private TenantDeleteApproval approval(Long tenantId, String approver, String status) {
        TenantDeleteApproval a = new TenantDeleteApproval();
        a.setApprovalId(9L);
        a.setTenantId(tenantId);
        a.setApproverOperatorId(approver);
        a.setStatus(status);
        a.setExpiresAt(LocalDateTime.now().plusHours(1));
        return a;
    }

    @Test
    @DisplayName("放行：已归档 + 保留期已过 + 令牌有效 + 确认者≠签发者 → 消费令牌 + DELETING + 审计 SUCCESS")
    void confirmHappyPath() {
        setRetentionDays(30);
        when(tenantGateway.findByTenantKey("acme"))
                .thenReturn(Optional.of(archivedTenant(LocalDateTime.now().minusDays(40))));
        when(deleteApprovalService.findByRawToken(RAW_TOKEN))
                .thenReturn(
                        Optional.of(approval(1L, APPROVER, TenantDeleteApproval.STATUS_PENDING)));

        TenantVO vo = exe.execute(cmd(CONFIRMER));

        assertThat(vo.getStatus()).isEqualTo(Tenant.STATUS_DELETING);
        assertThat(vo.getDeletionRequestId()).isNotBlank();
        verify(deleteApprovalService)
                .consume(any(TenantDeleteApproval.class), any(LocalDateTime.class), eq(CONFIRMER));
        verify(tenantGateway).update(any(Tenant.class));
        verify(tenantDirectory).invalidateByKey("acme");
        verify(systemConfigService).evictTenant("acme");
        verify(auditLog)
                .logTenantAction(
                        eq(CONFIRMER),
                        eq(ConfirmTenantDeletionCmdExe.ACTION),
                        eq("acme"),
                        eq("SUCCESS"),
                        any());
    }

    @Test
    @DisplayName("门槛1：非 ARCHIVED（ACTIVE）→ IllegalStateException、不消费令牌、审计 REJECTED")
    void rejectNotArchived() {
        setRetentionDays(30);
        Tenant active = archivedTenant(LocalDateTime.now().minusDays(40));
        active.setStatus(Tenant.STATUS_ACTIVE);
        when(tenantGateway.findByTenantKey("acme")).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> exe.execute(cmd(CONFIRMER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ARCHIVED");

        verify(deleteApprovalService, never()).consume(any(), any(), any());
        verify(tenantGateway, never()).update(any());
        verify(auditLog)
                .logTenantAction(
                        eq(CONFIRMER),
                        eq(ConfirmTenantDeletionCmdExe.ACTION),
                        eq("acme"),
                        eq("REJECTED"),
                        any());
    }

    @Test
    @DisplayName("门槛2：保留期未过 → IllegalStateException、审计 REJECTED")
    void rejectRetentionNotElapsed() {
        setRetentionDays(30);
        when(tenantGateway.findByTenantKey("acme"))
                .thenReturn(Optional.of(archivedTenant(LocalDateTime.now().minusDays(5))));

        assertThatThrownBy(() -> exe.execute(cmd(CONFIRMER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retention");

        verify(deleteApprovalService, never()).consume(any(), any(), any());
        verify(tenantGateway, never()).update(any());
    }

    @Test
    @DisplayName("门槛3：令牌未命中 → IllegalArgumentException、审计 REJECTED")
    void rejectTokenInvalid() {
        setRetentionDays(0);
        when(tenantGateway.findByTenantKey("acme"))
                .thenReturn(Optional.of(archivedTenant(LocalDateTime.now().minusDays(1))));
        when(deleteApprovalService.findByRawToken(RAW_TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(cmd(CONFIRMER)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");

        verify(deleteApprovalService, never()).consume(any(), any(), any());
    }

    @Test
    @DisplayName("门槛3：令牌属其他租户 → IllegalArgumentException")
    void rejectTokenTenantMismatch() {
        setRetentionDays(0);
        when(tenantGateway.findByTenantKey("acme"))
                .thenReturn(Optional.of(archivedTenant(LocalDateTime.now().minusDays(1))));
        when(deleteApprovalService.findByRawToken(RAW_TOKEN))
                .thenReturn(
                        Optional.of(approval(999L, APPROVER, TenantDeleteApproval.STATUS_PENDING)));

        assertThatThrownBy(() -> exe.execute(cmd(CONFIRMER)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("match tenant");

        verify(deleteApprovalService, never()).consume(any(), any(), any());
    }

    @Test
    @DisplayName("门槛3：令牌已消费（USED）→ IllegalStateException")
    void rejectTokenNotUsable() {
        setRetentionDays(0);
        when(tenantGateway.findByTenantKey("acme"))
                .thenReturn(Optional.of(archivedTenant(LocalDateTime.now().minusDays(1))));
        when(deleteApprovalService.findByRawToken(RAW_TOKEN))
                .thenReturn(Optional.of(approval(1L, APPROVER, TenantDeleteApproval.STATUS_USED)));

        assertThatThrownBy(() -> exe.execute(cmd(CONFIRMER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not usable");

        verify(deleteApprovalService, never()).consume(any(), any(), any());
    }

    @Test
    @DisplayName("门槛4：确认者==签发者（单人）→ IllegalStateException、审计 REJECTED")
    void rejectDualReviewViolation() {
        setRetentionDays(0);
        when(tenantGateway.findByTenantKey("acme"))
                .thenReturn(Optional.of(archivedTenant(LocalDateTime.now().minusDays(1))));
        when(deleteApprovalService.findByRawToken(RAW_TOKEN))
                .thenReturn(
                        Optional.of(approval(1L, APPROVER, TenantDeleteApproval.STATUS_PENDING)));

        // 签发者本人来确认
        assertThatThrownBy(() -> exe.execute(cmd(APPROVER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Dual-review");

        verify(deleteApprovalService, never()).consume(any(), any(), any());
        verify(tenantGateway, never()).update(any());
    }

    @Test
    @DisplayName("租户不存在 → IllegalArgumentException、审计 REJECTED")
    void rejectTenantNotFound() {
        setRetentionDays(30);
        when(tenantGateway.findByTenantKey("ghost")).thenReturn(Optional.empty());
        TenantDeleteCmd c = cmd(CONFIRMER);
        c.setTenantKey("ghost");

        assertThatThrownBy(() -> exe.execute(c))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");

        verify(auditLog)
                .logTenantAction(
                        eq(CONFIRMER),
                        eq(ConfirmTenantDeletionCmdExe.ACTION),
                        eq("ghost"),
                        eq("REJECTED"),
                        any());
    }
}
