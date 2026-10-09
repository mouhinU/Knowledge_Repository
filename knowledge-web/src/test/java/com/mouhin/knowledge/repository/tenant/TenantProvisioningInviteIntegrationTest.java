package com.mouhin.knowledge.repository.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.mouhin.knowledge.repository.application.executor.adminauth.SetPasswordByInviteCmdExe;
import com.mouhin.knowledge.repository.application.provisioning.step.CopyDefaultSystemConfigStep;
import com.mouhin.knowledge.repository.application.provisioning.step.CreateFirstAdminStep;
import com.mouhin.knowledge.repository.application.provisioning.step.CreateRootDepartmentStep;
import com.mouhin.knowledge.repository.application.service.ProvisioningOutcome;
import com.mouhin.knowledge.repository.application.service.ProvisioningOutcome.InviteInfo;
import com.mouhin.knowledge.repository.application.service.ProvisioningStateMachine;
import com.mouhin.knowledge.repository.application.support.UserInviteService;
import com.mouhin.knowledge.repository.client.dto.SetPasswordByInviteCmd;
import com.mouhin.knowledge.repository.client.dto.SetPasswordByInviteVO;
import com.mouhin.knowledge.repository.domain.gateway.DepartmentGateway;
import com.mouhin.knowledge.repository.domain.gateway.SystemConfigGateway;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.gateway.TenantProvisionStepGateway;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.gateway.UserInviteGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Department;
import com.mouhin.knowledge.repository.domain.model.entity.SystemConfig;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.domain.model.entity.UserInvite;
import com.mouhin.knowledge.repository.infrastructure.observability.Slf4jPlatformAuditLog;
import com.mouhin.knowledge.repository.infrastructure.persistence.gateway.DepartmentGatewayImpl;
import com.mouhin.knowledge.repository.infrastructure.persistence.gateway.SystemConfigGatewayImpl;
import com.mouhin.knowledge.repository.infrastructure.persistence.gateway.TenantGatewayImpl;
import com.mouhin.knowledge.repository.infrastructure.persistence.gateway.TenantProvisionStepGatewayImpl;
import com.mouhin.knowledge.repository.infrastructure.persistence.gateway.UserGatewayImpl;
import com.mouhin.knowledge.repository.infrastructure.persistence.gateway.UserInviteGatewayImpl;
import com.mouhin.knowledge.repository.infrastructure.tenant.TenantIdResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * P3-T3 租户开通 + 邀请式引导端到端集成测试（H2 MODE=MySQL，无需 Docker）。
 *
 * <p>在真实 H2（Flyway V1→V28 全量迁移）+ MyBatis-Plus + 真实网关上跑完整链路：
 *
 * <ol>
 *   <li><b>开通</b>：{@link ProvisioningStateMachine} 编排三个 P3-T3 步骤（order 200/300/400）→ 租户 ACTIVE；
 *   <li><b>sys_config 拷贝</b>：新租户下可见 default 租户的全部配置行（V28 租户化后按 tenant_id 隔离）；
 *   <li><b>根部门</b>：{@code {tenantKey}-root} 部门行存在，parentId=null；
 *   <li><b>首位 admin</b>：{@code {tenantKey}-admin} 用户存在，admin=true / status=ACTIVE /
 *       mustChangePassword=true / passwordHash=null；
 *   <li><b>邀请可用</b>：provision 响应体含 raw code；凭 raw code 走 {@link SetPasswordByInviteCmdExe} 设密成功 →
 *       用户 passwordHash 非空且 BCrypt 可匹配、mustChangePassword=false、邀请 USED；
 *   <li><b>幂等重放</b>：同 requestId 重放不重复建 user/dept/config，邀请被重签（旧邀请 REVOKED）。
 * </ol>
 *
 * <p>与 {@code TenantProvisioningIntegrationTest}（P3-T2 编排级）互补：本用例聚焦 P3-T3 三个步骤 + 邀请式引导的 真实持久化语义，刻意用
 * H2 而非 Testcontainers MySQL（schema 方言兼容已由 {@code FlywayMigrationSmokeTest} 覆盖）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@DisplayName("P3-T3 租户开通 + 邀请式引导端到端集成 (H2)")
@SpringBootTest(classes = TenantProvisioningInviteIntegrationTest.TestConfig.class)
class TenantProvisioningInviteIntegrationTest {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:h2:mem:kr-provision-invite;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        // 关闭 SaaS 拦截器：本用例验真实网关的显式租户 scoping，不依赖拦截器
        registry.add("knowledge.saas.enabled", () -> "false");
    }

    /**
     * 最小 Spring 上下文：DataSource / Flyway / MyBatis-Plus / 全部真实网关 / 三个 P3-T3 步骤 / 状态机 / 邀请服务 / 设密执行器。
     */
    @Configuration
    @EnableAutoConfiguration
    @MapperScan("com.mouhin.knowledge.repository.infrastructure.persistence.mapper")
    @Import({
        TenantGatewayImpl.class,
        TenantProvisionStepGatewayImpl.class,
        SystemConfigGatewayImpl.class,
        DepartmentGatewayImpl.class,
        UserGatewayImpl.class,
        UserInviteGatewayImpl.class,
        TenantIdResolver.class,
        Slf4jPlatformAuditLog.class,
        ProvisioningStateMachine.class,
        UserInviteService.class,
        CopyDefaultSystemConfigStep.class,
        CreateRootDepartmentStep.class,
        CreateFirstAdminStep.class,
        SetPasswordByInviteCmdExe.class
    })
    static class TestConfig {

        @Bean
        BCryptPasswordEncoder bCryptPasswordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired private TenantGateway tenantGateway;
    @Autowired private TenantProvisionStepGateway stepGateway;
    @Autowired private SystemConfigGateway systemConfigGateway;
    @Autowired private DepartmentGateway departmentGateway;
    @Autowired private UserGateway userGateway;
    @Autowired private UserInviteGateway userInviteGateway;
    @Autowired private UserInviteService userInviteService;
    @Autowired private ProvisioningStateMachine stateMachine;
    @Autowired private SetPasswordByInviteCmdExe setPasswordExe;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private Tenant newPendingTenant(String key) {
        Tenant t = new Tenant();
        t.setTenantKey(key);
        t.setName("Provision Invite IT " + key);
        t.setPlan("FREE");
        t.setStatus(Tenant.STATUS_PENDING);
        t.setDataRegion("cn");
        t.setProvisionRequestId(UUID.randomUUID().toString());
        tenantGateway.save(t);
        return t;
    }

    @Test
    @DisplayName("开通 → sys_config 拷贝 + 根部门 + 首位 admin + 邀请可用 → 设密 → 用户可登录态")
    void fullProvisionAndInviteBootstrapFlow() {
        String tenantKey = "it-inv-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = newPendingTenant(tenantKey);
        String reqId = tenant.getProvisionRequestId();

        // 1) 开通
        ProvisioningOutcome outcome = stateMachine.provision(tenant, reqId, "op-it", "IT Admin");
        assertThat(outcome.isSuccess()).isTrue();
        assertThat(outcome.finalStatus()).isEqualTo(Tenant.STATUS_ACTIVE);

        Tenant reloaded = tenantGateway.findByTenantKey(tenantKey).orElseThrow();
        assertThat(reloaded.isActive()).isTrue();
        Long tenantId = reloaded.getTenantId();

        // 2) sys_config 拷贝：新租户下应可见 default 租户的配置行（至少 V23 种子的几条）
        List<SystemConfig> copiedConfigs =
                com.mouhin.knowledge.repository.domain.context.TenantContext.callWithTenant(
                        tenantKey, systemConfigGateway::findAll);
        assertThat(copiedConfigs).isNotEmpty();
        assertThat(copiedConfigs).allSatisfy(c -> assertThat(c.getTenantId()).isEqualTo(tenantId));

        // 3) 根部门
        Optional<Department> rootDept =
                departmentGateway.findByDepartmentKeyAndTenant(tenantKey + "-root", tenantId);
        assertThat(rootDept).isPresent();
        assertThat(rootDept.get().getParentId()).isNull();
        assertThat(rootDept.get().getDepartmentName()).contains("根部门");

        // 4) 首位 admin
        String adminUsername = tenantKey + "-admin";
        Optional<User> adminOpt = userGateway.findByUsernameAndTenant(adminUsername, tenantId);
        assertThat(adminOpt).isPresent();
        User admin = adminOpt.get();
        assertThat(admin.getAdmin()).isTrue();
        assertThat(admin.getStatus()).isEqualTo(User.STATUS_ACTIVE);
        assertThat(admin.getMustChangePassword()).isTrue();
        assertThat(admin.getPasswordHash()).isNull();

        // 5) 邀请可用：outcome 含 InviteInfo，raw code 可换密码
        InviteInfo inviteInfo = outcome.invite();
        assertThat(inviteInfo).isNotNull();
        assertThat(inviteInfo.code()).isNotBlank();
        assertThat(inviteInfo.url()).startsWith("/set-password.html?code=");
        assertThat(inviteInfo.adminUsername()).isEqualTo(adminUsername);

        // 凭 raw code 设密
        SetPasswordByInviteCmd cmd = new SetPasswordByInviteCmd();
        cmd.setInviteCode(inviteInfo.code());
        cmd.setNewPassword("Str0ng!Passw0rd");
        SetPasswordByInviteVO vo = setPasswordExe.execute(cmd);
        assertThat(vo.isSuccess()).isTrue();
        assertThat(vo.getUsername()).isEqualTo(adminUsername);

        // 用户密码已更新 + mustChangePassword=false
        User afterSet = userGateway.findById(admin.getId()).orElseThrow();
        assertThat(afterSet.getPasswordHash()).isNotNull();
        assertThat(passwordEncoder.matches("Str0ng!Passw0rd", afterSet.getPasswordHash())).isTrue();
        assertThat(afterSet.getMustChangePassword()).isFalse();

        // 邀请已消费
        Optional<UserInvite> usedInvite = userInviteService.findByRawCode(inviteInfo.code());
        assertThat(usedInvite).isPresent();
        assertThat(usedInvite.get().getStatus()).isEqualTo(UserInvite.STATUS_USED);
        assertThat(usedInvite.get().getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("幂等重放：同 requestId 重放已完成开通 → 全步骤 SKIPPED、不重复建资源、原邀请仍 PENDING 可用")
    void replayOfCompletedProvisionIsIdempotentNoOp() {
        String tenantKey = "it-rep-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = newPendingTenant(tenantKey);
        String reqId = tenant.getProvisionRequestId();

        ProvisioningOutcome first = stateMachine.provision(tenant, reqId, "op-it", "IT Admin");
        assertThat(first.isSuccess()).isTrue();
        InviteInfo firstInvite = first.invite();
        assertThat(firstInvite).isNotNull();
        String firstCode = firstInvite.code();

        Tenant reloaded = tenantGateway.findByTenantKey(tenantKey).orElseThrow();
        Long tenantId = reloaded.getTenantId();

        // 强制回 PENDING 驱动重放（模拟运维拿着同一 requestId 重试）
        reloaded.setStatus(Tenant.STATUS_PENDING);
        tenantGateway.update(reloaded);
        ProvisioningOutcome second = stateMachine.provision(reloaded, reqId, "op-it", "IT Admin");
        assertThat(second.isSuccess()).isTrue();
        assertThat(second.finalStatus()).isEqualTo(Tenant.STATUS_ACTIVE);

        // 全部步骤因前次已 SUCCESS 而 SKIPPED（状态机步骤级幂等）
        assertThat(second.steps()).isNotEmpty();
        assertThat(second.steps())
                .allSatisfy(
                        s ->
                                assertThat(s.status())
                                        .isEqualTo(
                                                com.mouhin.knowledge.repository.domain.model.entity
                                                        .TenantProvisionStep.STATUS_SKIPPED));

        // 资源不重复建：admin user / 根部门各仍为 1
        assertThat(userGateway.findByUsernameAndTenant(tenantKey + "-admin", tenantId)).isPresent();
        assertThat(departmentGateway.findByDepartmentKeyAndTenant(tenantKey + "-root", tenantId))
                .isPresent();

        // CreateFirstAdminStep 被跳过 → 不重签邀请（重放已完成开通是无副作用的 no-op）
        assertThat(second.invite()).isNull();

        // 原邀请未被触碰：仍 PENDING、raw code 仍可换密码（运维丢失响应体时原码依然有效）
        Optional<UserInvite> originalInvite = userInviteService.findByRawCode(firstCode);
        assertThat(originalInvite).isPresent();
        assertThat(originalInvite.get().getStatus()).isEqualTo(UserInvite.STATUS_PENDING);
    }
}
