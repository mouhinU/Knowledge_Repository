package com.mouhin.knowledge.repository.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.gateway.SystemConfigGateway;
import com.mouhin.knowledge.repository.domain.model.entity.SystemConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link SystemConfigService} 租户缓存隔离单测（P4-T1 · {@link
 * com.mouhin.knowledge.repository.domain.model.valueobject.ConfigCacheKey} 收口）。
 *
 * <p>以内存桩顶替 {@link SystemConfigGateway}，桩按 {@link TenantContext} 当前绑定返回"租户作用域"结果（模拟 infra 层 {@code
 * SystemConfigGatewayImpl} 显式 {@code WHERE tenant_id=?} 的口径），据此验证服务进程内缓存的三条不变式：
 *
 * <ol>
 *   <li><b>同 key 不同租户不同值</b>：{@code ConfigCacheKey(tenant, key)} 天然区分，互不串味；
 *   <li><b>跨租户缓存不命中</b>：A 已缓存的条目绝不返回给 B（B 首访触发自身回源）；
 *   <li><b>前缀陷阱已消除</b>：旧实现 {@code evictTenant("acme")} 用 {@code startsWith("acme:")} 会连带清空 {@code
 *       acme2}；改用值对象按 {@code tenantKey} 精确匹配后，{@code acme2} 不受牵连。
 * </ol>
 *
 * @author mouhinU
 * @date 2026-10-10
 */
@Tag("config-isolation")
@DisplayName("SystemConfigService 租户缓存隔离 (P4-T1)")
class SystemConfigServiceTenantIsolationTest {

    private static final String KEY = "app.custom.tenant-key";
    private static final String FALLBACK_KEY = "exam.grading-delay-minutes";

    private SystemConfigGateway gateway;
    private SystemConfigService service;

    /** 桩后端：租户 → 业务配置值。缺省租户未播种 → 走内置默认。 */
    private java.util.Map<String, String> backend;

    @BeforeEach
    void setUp() {
        gateway = mock(SystemConfigGateway.class);
        service = new SystemConfigService(gateway);
        backend = new java.util.HashMap<>();
        backend.put("acme", "acme-value");
        backend.put("acme2", "acme2-value");
        // findByKey / findAll 均以"当前绑定租户"作用域，模拟网关显式 tenant 过滤。
        when(gateway.findByKey(any()))
                .thenAnswer(
                        inv -> {
                            String k = inv.getArgument(0);
                            String tenant = TenantContext.getOrDefault();
                            if (!KEY.equals(k)) {
                                return Optional.empty();
                            }
                            String v = backend.get(tenant);
                            return v == null ? Optional.empty() : Optional.of(config(tenant, k, v));
                        });
        when(gateway.findAll())
                .thenAnswer(
                        inv -> {
                            String tenant = TenantContext.getOrDefault();
                            String v = backend.get(tenant);
                            return v == null ? List.of() : List.of(config(tenant, KEY, v));
                        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static SystemConfig config(String tenant, String key, String value) {
        SystemConfig c = new SystemConfig();
        c.setConfigKey(key);
        c.setConfigValue(value);
        c.setEditable(true);
        return c;
    }

    @Nested
    @DisplayName("同 key 跨租户")
    class CrossTenant {

        @Test
        @DisplayName("相同 configKey 在不同租户返回各自值")
        void sameKeyDifferentTenantDifferentValue() {
            TenantContext.set("acme");
            assertThat(service.getString(KEY)).isEqualTo("acme-value");

            TenantContext.set("acme2");
            assertThat(service.getString(KEY)).isEqualTo("acme2-value");
        }

        @Test
        @DisplayName("A 已缓存的条目对 B 不命中（B 触发自身回源）")
        void cacheMissAcrossTenants() {
            TenantContext.set("acme");
            assertThat(service.getString(KEY)).isEqualTo("acme-value");
            long acmeLoads = 1; // 首次触发该租户全量加载
            verify(gateway, times((int) acmeLoads)).findAll();

            // 切到未缓存的 acme2：绝不应命中 acme 的缓存值，而应回源得到 acme2-value。
            TenantContext.set("acme2");
            assertThat(service.getString(KEY)).isEqualTo("acme2-value");
            verify(gateway, times(2)).findAll();

            // 回到 acme：已缓存，不再触发新的 findAll。
            TenantContext.set("acme");
            assertThat(service.getString(KEY)).isEqualTo("acme-value");
            verify(gateway, times(2)).findAll();
        }

        @Test
        @DisplayName("未播种租户回落到内置默认（Tier 3）")
        void unseededTenantFallsBackToDefault() {
            TenantContext.set("default");
            assertThat(service.getString(FALLBACK_KEY))
                    .isEqualTo(SystemConfigDefaults.getDefault(FALLBACK_KEY))
                    .isNotBlank();
        }
    }

    @Nested
    @DisplayName("evictTenant 精确性（前缀陷阱）")
    class EvictPrecision {

        @Test
        @DisplayName("清空 acme 不牵连 acme2（旧 startsWith 会误清）")
        void evictDoesNotAffectSiblingTenant() {
            TenantContext.set("acme");
            service.getString(KEY);
            TenantContext.set("acme2");
            service.getString(KEY);
            // 两租户各回源一次。
            verify(gateway, times(2)).findAll();

            service.evictTenant("acme");

            // 重新访问 acme：其加载标记被清，触发第 3 次回源。
            TenantContext.set("acme");
            assertThat(service.getString(KEY)).isEqualTo("acme-value");
            verify(gateway, times(3)).findAll();

            // acme2 仍处已加载态：不应因 evict("acme") 而额外回源。
            TenantContext.set("acme2");
            assertThat(service.getString(KEY)).isEqualTo("acme2-value");
            verify(gateway, times(3)).findAll();
            verify(gateway, never()).saveOrUpdate(any());
        }
    }
}
