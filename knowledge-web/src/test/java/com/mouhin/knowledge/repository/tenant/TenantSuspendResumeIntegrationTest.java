package com.mouhin.knowledge.repository.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.mouhin.knowledge.repository.application.executor.platform.TenantResumeCmdExe;
import com.mouhin.knowledge.repository.application.executor.platform.TenantSuspendCmdExe;
import com.mouhin.knowledge.repository.application.support.SystemConfigService;
import com.mouhin.knowledge.repository.client.dto.TenantStatusCmd;
import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.gateway.ConfigInvalidationPublisher;
import com.mouhin.knowledge.repository.domain.gateway.TenantDirectory;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.infrastructure.observability.Slf4jPlatformAuditLog;
import com.mouhin.knowledge.repository.infrastructure.persistence.gateway.SystemConfigGatewayImpl;
import com.mouhin.knowledge.repository.infrastructure.persistence.gateway.TenantGatewayImpl;
import com.mouhin.knowledge.repository.infrastructure.tenant.TenantIdResolver;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * P3-T4 租户软停 / 恢复 + 缓存即时失效端到端集成测试（H2 MODE=MySQL，无需 Docker）。
 *
 * <p>在真实 H2（Flyway V1→V28）+ MyBatis-Plus + 真实 {@link TenantGatewayImpl} / {@link
 * TenantIdResolver}（作 {@link TenantDirectory}）/ {@link SystemConfigService} 上跑真实执行器，验证 P3-T4
 * 的"即时生效"语义：
 *
 * <ol>
 *   <li><b>软停</b>：{@link TenantSuspendCmdExe} 落库 SUSPENDED 后，{@code
 *       TenantDirectory.findStatusByKey}（请求路径软拦读它） 立即返回 SUSPENDED（缓存已被执行器失效），且 {@code
 *       TenantIdResolver.resolve} 因非 ACTIVE 抛异常；
 *   <li><b>恢复</b>：{@link TenantResumeCmdExe} 落库 ACTIVE 后，{@code findStatusByKey} 立即返回 ACTIVE，{@code
 *       resolve} 恢复可用——即"resume 立即恢复"；
 *   <li>配置缓存在软停 / 恢复时被 evict，恢复后仍可正常读取该租户配置。
 * </ol>
 *
 * <p>过滤器层的 403 {@code TENANT_SUSPENDED} 断言由 {@code TenantResolverFilterTest}（软拦 nested）覆盖； 本用例聚焦执行器
 * → 网关 → 缓存失效的真实持久化链路（{@code saas.enabled=false}，不挂租户拦截器）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@DisplayName("P3-T4 软停/恢复 + 缓存即时失效集成 (H2)")
@SpringBootTest(classes = TenantSuspendResumeIntegrationTest.TestConfig.class)
class TenantSuspendResumeIntegrationTest {

    @Autowired private TenantGateway tenantGateway;
    @Autowired private TenantDirectory tenantDirectory;
    @Autowired private TenantIdResolver tenantIdResolver;
    @Autowired private SystemConfigService systemConfigService;
    @Autowired private TenantSuspendCmdExe suspendExe;
    @Autowired private TenantResumeCmdExe resumeExe;

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:h2:mem:kr-suspend-resume;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("knowledge.saas.enabled", () -> "false");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Tenant newActiveTenant(String key) {
        Tenant t = new Tenant();
        t.setTenantKey(key);
        t.setName("Suspend IT " + key);
        t.setPlan("FREE");
        t.setStatus(Tenant.STATUS_ACTIVE);
        t.setDataRegion("cn");
        t.setProvisionRequestId(UUID.randomUUID().toString());
        tenantGateway.save(t);
        return t;
    }

    private TenantStatusCmd cmd(String key) {
        TenantStatusCmd c = new TenantStatusCmd();
        c.setTenantKey(key);
        c.setOperatorId("op-it");
        c.setReason("integration");
        return c;
    }

    @Test
    @DisplayName("软停 → 目录状态即时 SUSPENDED + resolve 抛非 ACTIVE；恢复 → 即时 ACTIVE + resolve 复位")
    void suspendThenResumeFlipsStatusAndInvalidatesCache() {
        String key = "it-sus-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = newActiveTenant(key);
        Long tenantId = tenant.getTenantId();

        // 预热缓存：ACTIVE 可读、resolve 可用
        assertThat(tenantDirectory.findStatusByKey(key)).contains(Tenant.STATUS_ACTIVE);
        assertThat(tenantIdResolver.resolve(key)).isEqualTo(tenantId);

        // 软停
        suspendExe.execute(cmd(key));

        Tenant suspended = tenantGateway.findByTenantKey(key).orElseThrow();
        assertThat(suspended.isSuspended()).isTrue();
        // 执行器已失效目录缓存 → 下次读取回源即见 SUSPENDED（软拦据此 403）
        assertThat(tenantDirectory.findStatusByKey(key)).contains(Tenant.STATUS_SUSPENDED);
        // resolve 对非 ACTIVE 租户严格抛异常（缓存已失效，回源判定）
        assertThatThrownBy(() -> tenantIdResolver.resolve(key))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not ACTIVE");

        // 恢复
        resumeExe.execute(cmd(key));

        Tenant resumed = tenantGateway.findByTenantKey(key).orElseThrow();
        assertThat(resumed.isActive()).isTrue();
        assertThat(tenantDirectory.findStatusByKey(key)).contains(Tenant.STATUS_ACTIVE);
        assertThat(tenantIdResolver.resolve(key)).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("软停/恢复 evict 配置缓存后，恢复态下该租户配置仍可正常读取")
    void configCacheEvictedAndReloadableAfterResume() {
        String key = "it-cfg-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = newActiveTenant(key);

        suspendExe.execute(cmd(key));
        resumeExe.execute(cmd(key));

        // 恢复后按该租户上下文读配置：evict 后首次访问回源（default 租户种子行不属本租户，故本租户视图为空但不报错）
        var configs = TenantContext.callWithTenant(key, () -> systemConfigService.listAll());
        assertThat(configs).isNotNull();
        assertThat(tenantDirectory.findStatusByKey(key)).contains(Tenant.STATUS_ACTIVE);
        assertThat(tenant.getTenantId()).isNotNull();
    }

    /** 最小上下文：DataSource / Flyway / MyBatis-Plus / 真实网关 + 目录 + 配置服务 + 软停/恢复执行器。 */
    @Configuration
    @EnableAutoConfiguration
    @MapperScan("com.mouhin.knowledge.repository.infrastructure.persistence.mapper")
    @Import({
        TenantGatewayImpl.class,
        TenantIdResolver.class,
        Slf4jPlatformAuditLog.class,
        SystemConfigGatewayImpl.class,
        SystemConfigService.class,
        TenantSuspendCmdExe.class,
        TenantResumeCmdExe.class
    })
    static class TestConfig {

        /** P4-T2: ConfigInvalidationPublisher 以 mock 顶替（测试环境无 Redis）。 */
        @Bean
        ConfigInvalidationPublisher configInvalidationPublisher() {
            return mock(ConfigInvalidationPublisher.class);
        }
    }
}
