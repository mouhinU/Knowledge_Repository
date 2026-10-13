package com.mouhin.knowledge.repository.application.provisioning.step;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mouhin.knowledge.repository.application.service.ProvisioningOutcome.InviteInfo;
import com.mouhin.knowledge.repository.application.support.UserInviteService;
import com.mouhin.knowledge.repository.application.support.UserInviteService.InviteIssueResult;
import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.gateway.DepartmentGateway;
import com.mouhin.knowledge.repository.domain.gateway.SystemConfigGateway;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Department;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.domain.model.entity.UserInvite;
import com.mouhin.knowledge.repository.domain.provisioning.ProvisioningContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * P3-T3 三个开通步骤的单元测试：{@link CopyDefaultSystemConfigStep}（order=200）、{@link
 * CreateRootDepartmentStep}（order=300）、{@link CreateFirstAdminStep}（order=400）。
 *
 * <p>锁定各步骤的幂等语义、命名约定、错误防御与上下文回写：
 *
 * <ul>
 *   <li>CopyDefaultSystemConfig：从 default 租户拷贝到新租户；source==target 时跳过；default 租户缺失抛错。
 *   <li>CreateRootDepartment：departmentKey = {@code {tenantKey}-root}；已存在则跳过；name 取 tenant.name + "
 *       根部门"。
 *   <li>CreateFirstAdmin：username = {@code {tenantKey}-admin}；password_hash=null /
 *       mustChangePassword=true / admin=true；无论 user 是否复用都重签邀请；把 {@link InviteInfo} 写入 {@link
 *       ProvisioningContext#attributes()}。
 * </ul>
 *
 * <p>全部用 mock 网关，不启 Spring；与 {@code ProvisioningStateMachineTest} 的编排级测试互补。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@DisplayName("P3-T3 开通步骤单元测试")
class ProvisioningStepsTest {

    private static final long DEFAULT_TENANT_ID = 1L;
    private static final long ACME_TENANT_ID = 42L;
    private static final String ACME_KEY = "acme";

    private Tenant acmeTenant() {
        Tenant t = new Tenant();
        t.setTenantId(ACME_TENANT_ID);
        t.setTenantKey(ACME_KEY);
        t.setName("Acme 制造");
        t.setStatus(Tenant.STATUS_PROVISIONING);
        return t;
    }

    private Tenant defaultTenant() {
        Tenant t = new Tenant();
        t.setTenantId(DEFAULT_TENANT_ID);
        t.setTenantKey(TenantContext.DEFAULT_TENANT_KEY);
        t.setName("Default");
        t.setStatus(Tenant.STATUS_ACTIVE);
        return t;
    }

    private ProvisioningContext ctxFor(Tenant tenant) {
        return new ProvisioningContext(tenant, "req-test", "op-1", "admin");
    }

    // ==================== CopyDefaultSystemConfigStep ====================

    @Nested
    @DisplayName("CopyDefaultSystemConfigStep (order=200)")
    class CopyDefaultSystemConfig {

        @Test
        @DisplayName("name/order 常量与状态机排序契约对齐")
        void nameAndOrder() {
            SystemConfigGateway cfg = mock(SystemConfigGateway.class);
            TenantGateway tg = mock(TenantGateway.class);
            CopyDefaultSystemConfigStep step = new CopyDefaultSystemConfigStep(cfg, tg);

            assertThat(step.name()).isEqualTo("COPY_DEFAULT_SYS_CONFIG");
            assertThat(step.order()).isEqualTo(200);
        }

        @Test
        @DisplayName("正常路径：从 default(1) 拷贝到 acme(42)，返回拷贝行数")
        void copiesFromDefaultToTarget() {
            SystemConfigGateway cfg = mock(SystemConfigGateway.class);
            TenantGateway tg = mock(TenantGateway.class);
            when(tg.findByTenantKey(TenantContext.DEFAULT_TENANT_KEY))
                    .thenReturn(Optional.of(defaultTenant()));
            when(cfg.copyAllFromTenantToTenant(DEFAULT_TENANT_ID, ACME_TENANT_ID)).thenReturn(7);

            new CopyDefaultSystemConfigStep(cfg, tg).execute(ctxFor(acmeTenant()));

            verify(cfg).copyAllFromTenantToTenant(DEFAULT_TENANT_ID, ACME_TENANT_ID);
        }

        @Test
        @DisplayName("自拷贝防御：target == default 时静默跳过，不调 gateway")
        void skipsSelfCopy() {
            SystemConfigGateway cfg = mock(SystemConfigGateway.class);
            TenantGateway tg = mock(TenantGateway.class);
            when(tg.findByTenantKey(TenantContext.DEFAULT_TENANT_KEY))
                    .thenReturn(Optional.of(defaultTenant()));

            Tenant defaultAsTarget = defaultTenant();
            new CopyDefaultSystemConfigStep(cfg, tg).execute(ctxFor(defaultAsTarget));

            verify(cfg, never()).copyAllFromTenantToTenant(any(Long.class), any(Long.class));
        }

        @Test
        @DisplayName("default 租户缺失（V23 种子漂移）→ IllegalStateException 带诊断信息")
        void throwsWhenDefaultTenantMissing() {
            SystemConfigGateway cfg = mock(SystemConfigGateway.class);
            TenantGateway tg = mock(TenantGateway.class);
            when(tg.findByTenantKey(TenantContext.DEFAULT_TENANT_KEY)).thenReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    new CopyDefaultSystemConfigStep(cfg, tg)
                                            .execute(ctxFor(acmeTenant())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Default tenant")
                    .hasMessageContaining(TenantContext.DEFAULT_TENANT_KEY);
        }

        @Test
        @DisplayName("context.tenantId() 为 null → IllegalStateException")
        void throwsWhenTargetTenantIdNull() {
            SystemConfigGateway cfg = mock(SystemConfigGateway.class);
            TenantGateway tg = mock(TenantGateway.class);
            Tenant noId = new Tenant();
            noId.setTenantKey(ACME_KEY);

            assertThatThrownBy(() -> new CopyDefaultSystemConfigStep(cfg, tg).execute(ctxFor(noId)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("tenantId is null");
        }
    }

    // ==================== CreateRootDepartmentStep ====================

    @Nested
    @DisplayName("CreateRootDepartmentStep (order=300)")
    class CreateRootDepartment {

        @Test
        @DisplayName("name/order 常量")
        void nameAndOrder() {
            DepartmentGateway dg = mock(DepartmentGateway.class);
            CreateRootDepartmentStep step = new CreateRootDepartmentStep(dg);

            assertThat(step.name()).isEqualTo("CREATE_ROOT_DEPARTMENT");
            assertThat(step.order()).isEqualTo(300);
        }

        @Test
        @DisplayName("正常路径：departmentKey = acme-root，name = 'Acme 制造 根部门'，parentId=null")
        void createsRootDepartment() {
            DepartmentGateway dg = mock(DepartmentGateway.class);
            when(dg.findByDepartmentKeyAndTenant("acme-root", ACME_TENANT_ID))
                    .thenReturn(Optional.empty());

            new CreateRootDepartmentStep(dg).execute(ctxFor(acmeTenant()));

            ArgumentCaptor<Department> cap = ArgumentCaptor.forClass(Department.class);
            verify(dg).saveWithTenant(cap.capture(), eq(ACME_TENANT_ID));
            Department saved = cap.getValue();
            assertThat(saved.getDepartmentKey()).isEqualTo("acme-root");
            assertThat(saved.getDepartmentName()).isEqualTo("Acme 制造 根部门");
            assertThat(saved.getParentId()).isNull();
        }

        @Test
        @DisplayName("幂等：根部门已存在 → 跳过，不再 saveWithTenant")
        void skipsWhenAlreadyExists() {
            DepartmentGateway dg = mock(DepartmentGateway.class);
            Department existing = new Department();
            existing.setId(99L);
            existing.setDepartmentKey("acme-root");
            when(dg.findByDepartmentKeyAndTenant("acme-root", ACME_TENANT_ID))
                    .thenReturn(Optional.of(existing));

            new CreateRootDepartmentStep(dg).execute(ctxFor(acmeTenant()));

            verify(dg, never()).saveWithTenant(any(), any(Long.class));
        }

        @Test
        @DisplayName("tenant.name 为空 → 显示名回落 tenantKey + ' 根部门'")
        void fallsBackToTenantKeyWhenNameBlank() {
            DepartmentGateway dg = mock(DepartmentGateway.class);
            when(dg.findByDepartmentKeyAndTenant(any(), anyLong())).thenReturn(Optional.empty());
            Tenant t = acmeTenant();
            t.setName("   ");

            new CreateRootDepartmentStep(dg).execute(ctxFor(t));

            ArgumentCaptor<Department> cap = ArgumentCaptor.forClass(Department.class);
            verify(dg).saveWithTenant(cap.capture(), eq(ACME_TENANT_ID));
            assertThat(cap.getValue().getDepartmentName()).isEqualTo("acme 根部门");
        }

        @Test
        @DisplayName("tenantId/tenantKey 缺失 → IllegalStateException")
        void throwsWhenTenantInfoMissing() {
            DepartmentGateway dg = mock(DepartmentGateway.class);
            Tenant noKey = new Tenant();
            noKey.setTenantId(ACME_TENANT_ID);

            assertThatThrownBy(() -> new CreateRootDepartmentStep(dg).execute(ctxFor(noKey)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Tenant id/key required");
        }
    }

    // ==================== CreateFirstAdminStep ====================

    @Nested
    @DisplayName("CreateFirstAdminStep (order=400)")
    class CreateFirstAdmin {

        @Test
        @DisplayName("name/order 常量")
        void nameAndOrder() {
            UserGateway ug = mock(UserGateway.class);
            UserInviteService uis = mock(UserInviteService.class);
            CreateFirstAdminStep step = new CreateFirstAdminStep(ug, uis);

            assertThat(step.name()).isEqualTo("CREATE_FIRST_ADMIN");
            assertThat(step.order()).isEqualTo(400);
        }

        @Test
        @DisplayName(
                "正常路径：建 user(admin/ACTIVE/mustChangePwd/pwdHash=null) + 签邀请 + 写 InviteInfo 到 ctx.attributes")
        void createsAdminAndIssuesInvite() {
            UserGateway ug = mock(UserGateway.class);
            UserInviteService uis = mock(UserInviteService.class);
            when(ug.findByUsernameAndTenant("acme-admin", ACME_TENANT_ID))
                    .thenReturn(Optional.empty());
            // saveWithTenant 回填 id
            org.mockito.Mockito.doAnswer(
                            inv -> {
                                inv.getArgument(0, User.class).setId(500L);
                                return null;
                            })
                    .when(ug)
                    .saveWithTenant(any(User.class), eq(ACME_TENANT_ID));

            UserInvite issued = new UserInvite();
            issued.setInviteId(77L);
            issued.setExpiresAt(LocalDateTime.now().plusHours(72));
            when(uis.issue(eq(ACME_TENANT_ID), eq(500L), any(Duration.class)))
                    .thenReturn(new InviteIssueResult(issued, "raw-code-xyz"));

            ProvisioningContext ctx = ctxFor(acmeTenant());
            new CreateFirstAdminStep(ug, uis).execute(ctx);

            ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
            verify(ug).saveWithTenant(cap.capture(), eq(ACME_TENANT_ID));
            User saved = cap.getValue();
            assertThat(saved.getUsername()).isEqualTo("acme-admin");
            assertThat(saved.getAdmin()).isTrue();
            assertThat(saved.getStatus()).isEqualTo(User.STATUS_ACTIVE);
            assertThat(saved.getMustChangePassword()).isTrue();
            assertThat(saved.getPasswordHash()).isNull();
            assertThat(saved.getUserKey()).isNotBlank();

            InviteInfo info =
                    (InviteInfo) ctx.attributes().get(ProvisioningContext.ATTR_INVITE_INFO);
            assertThat(info).isNotNull();
            assertThat(info.code()).isEqualTo("raw-code-xyz");
            assertThat(info.url()).isEqualTo("/set-password.html?code=raw-code-xyz");
            assertThat(info.adminUsername()).isEqualTo("acme-admin");
            assertThat(info.expiresAt()).isEqualTo(issued.getExpiresAt());
        }

        @Test
        @DisplayName("幂等：user 已存在 → 复用，不重复建；仍重签邀请（旧邀请被 issue 内部吊销）")
        void reusesExistingUserAndReissuesInvite() {
            UserGateway ug = mock(UserGateway.class);
            UserInviteService uis = mock(UserInviteService.class);
            User existing = new User();
            existing.setId(500L);
            existing.setUsername("acme-admin");
            existing.setAdmin(true);
            existing.setStatus(User.STATUS_ACTIVE);
            when(ug.findByUsernameAndTenant("acme-admin", ACME_TENANT_ID))
                    .thenReturn(Optional.of(existing));

            UserInvite issued = new UserInvite();
            issued.setInviteId(78L);
            issued.setExpiresAt(LocalDateTime.now().plusHours(72));
            when(uis.issue(eq(ACME_TENANT_ID), eq(500L), any(Duration.class)))
                    .thenReturn(new InviteIssueResult(issued, "new-raw-code"));

            ProvisioningContext ctx = ctxFor(acmeTenant());
            new CreateFirstAdminStep(ug, uis).execute(ctx);

            verify(ug, never()).saveWithTenant(any(), any(Long.class));
            verify(uis, times(1)).issue(eq(ACME_TENANT_ID), eq(500L), any(Duration.class));
            InviteInfo info =
                    (InviteInfo) ctx.attributes().get(ProvisioningContext.ATTR_INVITE_INFO);
            assertThat(info.code()).isEqualTo("new-raw-code");
        }

        @Test
        @DisplayName("saveWithTenant 未回填 id → IllegalStateException（防 NPE  downstream）")
        void throwsWhenIdNotPopulated() {
            UserGateway ug = mock(UserGateway.class);
            UserInviteService uis = mock(UserInviteService.class);
            when(ug.findByUsernameAndTenant(any(), anyLong())).thenReturn(Optional.empty());
            // saveWithTenant 不回填 id（模拟异常网关行为）

            assertThatThrownBy(
                            () -> new CreateFirstAdminStep(ug, uis).execute(ctxFor(acmeTenant())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("id was not populated");
        }

        @Test
        @DisplayName("tenantId/tenantKey 缺失 → IllegalStateException")
        void throwsWhenTenantInfoMissing() {
            UserGateway ug = mock(UserGateway.class);
            UserInviteService uis = mock(UserInviteService.class);
            Tenant noKey = new Tenant();
            noKey.setTenantId(ACME_TENANT_ID);

            assertThatThrownBy(() -> new CreateFirstAdminStep(ug, uis).execute(ctxFor(noKey)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Tenant id/key required");
        }
    }
}
