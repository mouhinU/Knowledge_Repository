package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.client.dto.TenantCreateCmd;
import com.mouhin.knowledge.repository.client.dto.TenantVO;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link TenantCreateCmdExe} 平台端开通租户执行器单测（P3-T1）。
 *
 * <p>锁定：合法键归一化落库为 PENDING（P3-T2：创建不直接开通，生成 UUID v4 provisionRequestId）、plan 缺省 FREE、审计 SUCCESS；非法键
 * / 重复键拒绝且不落库、审计 REJECTED。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@DisplayName("平台端开通租户执行器 (TenantCreateCmdExe)")
class TenantCreateCmdExeTest {

    private final TenantGateway tenantGateway = mock(TenantGateway.class);
    private final PlatformAuditLog auditLog = mock(PlatformAuditLog.class);
    private final TenantCreateCmdExe exe = new TenantCreateCmdExe(tenantGateway, auditLog);

    private TenantCreateCmd cmd(String key, String name, String plan) {
        TenantCreateCmd c = new TenantCreateCmd();
        c.setTenantKey(key);
        c.setName(name);
        c.setPlan(plan);
        c.setOperatorId("op-1");
        c.setOperatorName("platform-admin");
        return c;
    }

    @Test
    @DisplayName("合法键 → 归一化小写落库、status=PENDING、生成 provisionRequestId、plan 直传、审计 SUCCESS")
    void createsPendingTenant() {
        when(tenantGateway.findByTenantKey("acme")).thenReturn(Optional.empty());

        TenantVO vo = exe.execute(cmd("  acme  ", "Acme Inc", "ENTERPRISE"));

        ArgumentCaptor<Tenant> cap = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantGateway, times(1)).save(cap.capture());
        Tenant saved = cap.getValue();
        assertThat(saved.getTenantKey()).isEqualTo("acme");
        assertThat(saved.getStatus()).isEqualTo(Tenant.STATUS_PENDING);
        assertThat(saved.getProvisionRequestId()).isNotBlank();
        assertThat(saved.getPlan()).isEqualTo("ENTERPRISE");
        assertThat(saved.getName()).isEqualTo("Acme Inc");
        assertThat(vo.getTenantKey()).isEqualTo("acme");
        assertThat(vo.getStatus()).isEqualTo(Tenant.STATUS_PENDING);
        verify(auditLog)
                .logTenantAction(
                        eq("op-1"),
                        eq(TenantCreateCmdExe.ACTION),
                        eq("acme"),
                        eq("SUCCESS"),
                        any());
    }

    @Test
    @DisplayName("plan 缺省 → 落 FREE")
    void defaultsPlanToFree() {
        when(tenantGateway.findByTenantKey("acme")).thenReturn(Optional.empty());

        exe.execute(cmd("acme", "Acme", null));

        ArgumentCaptor<Tenant> cap = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantGateway).save(cap.capture());
        assertThat(cap.getValue().getPlan()).isEqualTo("FREE");
    }

    @Test
    @DisplayName("非法键 → 抛 IllegalArgumentException、不落库、审计 REJECTED")
    void rejectsInvalidKey() {
        assertThatThrownBy(() -> exe.execute(cmd("Acme", "x", "FREE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenantKey");

        verify(tenantGateway, never()).save(any());
        verify(auditLog)
                .logTenantAction(
                        eq("op-1"),
                        eq(TenantCreateCmdExe.ACTION),
                        eq("Acme"),
                        eq("REJECTED"),
                        any());
    }

    @Test
    @DisplayName("重复键 → 抛 IllegalStateException、不落库、审计 REJECTED")
    void rejectsDuplicateKey() {
        when(tenantGateway.findByTenantKey("acme")).thenReturn(Optional.of(new Tenant()));

        assertThatThrownBy(() -> exe.execute(cmd("acme", "x", "FREE")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");

        verify(tenantGateway, never()).save(any());
        verify(auditLog)
                .logTenantAction(
                        eq("op-1"),
                        eq(TenantCreateCmdExe.ACTION),
                        eq("acme"),
                        eq("REJECTED"),
                        any());
    }
}
