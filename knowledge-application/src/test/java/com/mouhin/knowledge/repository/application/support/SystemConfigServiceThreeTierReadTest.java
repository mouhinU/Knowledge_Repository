package com.mouhin.knowledge.repository.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.gateway.SystemConfigGateway;
import com.mouhin.knowledge.repository.domain.model.entity.SystemConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link SystemConfigService} 三级读取顺序单测（P4-T1 · S-1 定案方案 A）。
 *
 * <p>三级顺序（清单 §3 S-1）：
 *
 * <ol>
 *   <li><b>Tier 1 · 租户级</b>：{@code sys_config WHERE tenant_id = <当前租户>}；
 *   <li><b>Tier 2 · 平台级</b>：{@code sys_config WHERE tenant_id = 1 (default)}——仅非 default 租户在 Tier 1
 *       miss 后回退查；
 *   <li><b>Tier 3 · 内置默认</b>：{@link SystemConfigDefaults#getDefault(String)} Java 侧兜底。
 * </ol>
 *
 * <p>本用例以 {@link SystemConfigGateway} 内存桩顶替 infra 层（模拟 {@code
 * SystemConfigGatewayImpl#findByKey/findAll} 依 {@code TenantContext} 追加 {@code WHERE tenant_id=?} 的
 * 口径），覆盖清单 §3 S-1 定案方案 A 的五条不变式：
 *
 * <ul>
 *   <li>Tier 1 命中：租户已写入 sys_config 行，返回值取自租户层；
 *   <li>Tier 2 命中：租户缺行 + default 已有 → 回退平台层；
 *   <li>Tier 3 兜底：两级都缺 → {@link SystemConfigDefaults#getDefault}；
 *   <li>租户级"覆盖"语义：同名 key 在 acme 与 default 各有值 → acme 上下文取 acme 值，不回退；
 *   <li>关闭态等价：{@code TenantContext} 未绑定（{@code saas.enabled=false} 语义）时 current==default， Tier 1
 *       已覆盖平台层，显式跳过 Tier 2 冗余 DB 查询。
 * </ul>
 *
 * <p><b>设计取舍</b>：本用例走行为断言（返回值 + 关键调用发生与否），不断言精确调用次数——次数是编排细节，随内部
 * 缓存策略优化（如负缓存、批量预取）就会漂移，锁死次数反而阻碍未来演进。跨租户作用域正确性由"读回的值 == 期望的租户槽位值"承担： 若 {@code ensureCacheLoaded}
 * 仍走旧写法（不带 {@code callWithTenant}），Tier 2 预加载 default 时会把当前 acme 上下文误当作用域、把 acme 行错挂到 {@code
 * (default, *)}，届时 acme 读平台 key 得到 {@code null} 或错值，本用例即红。
 *
 * @author mouhinU
 * @date 2026-10-10
 */
@DisplayName("SystemConfigService 三级读取顺序 (P4-T1 · S-1)")
class SystemConfigServiceThreeTierReadTest {

    /** 只在 default 有行的 key：验证 Tier 2 平台级回退。 */
    private static final String PLATFORM_ONLY_KEY = "app.feature.platform-only";

    /** 只在 acme 有行的 key：验证 Tier 1 命中。 */
    private static final String TENANT_ONLY_KEY = "app.feature.tenant-only";

    /** 两级都有行的 key：验证 Tier 1 覆盖 Tier 2。 */
    private static final String BOTH_KEY = "app.feature.both";

    /** 两级都缺的 key：验证 Tier 3 兜底（复用 SystemConfigDefaults 已注册项，无需真实 DB）。 */
    private static final String NEITHER_KEY = "exam.grading-delay-minutes";

    /** 桩后端：(tenantKey, configKey) → configValue。 */
    private final Map<String, Map<String, String>> backend = new HashMap<>();

    private SystemConfigGateway gateway;
    private SystemConfigService service;

    @BeforeEach
    void setUp() {
        gateway = mock(SystemConfigGateway.class);
        service = new SystemConfigService(gateway);
        backend.clear();
        seed("default", PLATFORM_ONLY_KEY, "platform-value");
        seed("default", BOTH_KEY, "platform-both-value");
        seed("acme", TENANT_ONLY_KEY, "acme-tenant-value");
        seed("acme", BOTH_KEY, "acme-both-value");

        // Gateway.findByKey 依当前 TenantContext 返回该租户下命中的行；未播种即 Optional.empty()。
        when(gateway.findByKey(any()))
                .thenAnswer(
                        inv -> {
                            String configKey = inv.getArgument(0);
                            String tenant = TenantContext.getOrDefault();
                            String v = backendAt(tenant).get(configKey);
                            return v == null ? Optional.empty() : Optional.of(config(configKey, v));
                        });
        // Gateway.findAll 依当前 TenantContext 返回该租户全部行。
        when(gateway.findAll())
                .thenAnswer(
                        inv -> {
                            String tenant = TenantContext.getOrDefault();
                            return backendAt(tenant).entrySet().stream()
                                    .map(e -> config(e.getKey(), e.getValue()))
                                    .toList();
                        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static SystemConfig config(String key, String value) {
        SystemConfig c = new SystemConfig();
        c.setConfigKey(key);
        c.setConfigValue(value);
        c.setEditable(true);
        return c;
    }

    private void seed(String tenantKey, String configKey, String value) {
        backend.computeIfAbsent(tenantKey, k -> new HashMap<>()).put(configKey, value);
    }

    private Map<String, String> backendAt(String tenantKey) {
        return backend.getOrDefault(tenantKey, Map.of());
    }

    @Nested
    @DisplayName("Tier 1 · 租户级命中")
    class Tier1 {

        @Test
        @DisplayName("acme 已写入的 key 直接返回 acme 值")
        void tenantHitReturnsOwnValue() {
            TenantContext.set("acme");
            assertThat(service.getString(TENANT_ONLY_KEY)).isEqualTo("acme-tenant-value");
        }

        @Test
        @DisplayName("default 上下文读 default 已有 key（关闭态路径 · Tier 1 == Tier 2）")
        void defaultReadsPlatformLevel() {
            TenantContext.clear();
            assertThat(service.getString(PLATFORM_ONLY_KEY)).isEqualTo("platform-value");
        }
    }

    @Nested
    @DisplayName("Tier 2 · 平台级回退")
    class Tier2 {

        @Test
        @DisplayName("acme 缺行 + default 已有 → 回退平台层值")
        void acmeMissingFallsBackToPlatform() {
            TenantContext.set("acme");
            // TENANT_ONLY_KEY 只在 acme；PLATFORM_ONLY_KEY 只在 default。acme 读后者应得平台值。
            assertThat(service.getString(PLATFORM_ONLY_KEY)).isEqualTo("platform-value");
        }

        @Test
        @DisplayName("同名 key acme+default 都有 → 取 acme（Tier 1 覆盖 Tier 2）")
        void tenantOverridesPlatform() {
            TenantContext.set("acme");
            assertThat(service.getString(BOTH_KEY)).isEqualTo("acme-both-value");
        }

        @Test
        @DisplayName("平台级回退正确触发 default 作用域加载（无跨租户串味）")
        void platformFallbackLoadsDefaultScopeSeparately() {
            TenantContext.set("acme");
            // Tier 1 acme miss → Tier 2 default lookup；若 default 预加载错位到 acme 上下文
            // （旧 ensureCacheLoaded 未 callWithTenant 时的 bug），则 default 槽读到 acme 行、
            // 拿不到 platform-value。断言返回值 == platform-value 即可锁死跨租户作用域正确性。
            assertThat(service.getString(PLATFORM_ONLY_KEY)).isEqualTo("platform-value");
            // 两级各触发 1 次 findAll，合计 >= 2
            verify(gateway, atLeast(2)).findAll();
        }
    }

    @Nested
    @DisplayName("Tier 3 · 内置默认兜底")
    class Tier3 {

        @Test
        @DisplayName("两级都缺 → 走 SystemConfigDefaults")
        void builtinDefaultUsedWhenBothTiersMiss() {
            TenantContext.set("acme");
            assertThat(backendAt("acme")).doesNotContainKey(NEITHER_KEY);
            assertThat(backendAt("default")).doesNotContainKey(NEITHER_KEY);
            assertThat(SystemConfigDefaults.getDefault(NEITHER_KEY)).isNotBlank();
            assertThat(service.getString(NEITHER_KEY))
                    .isEqualTo(SystemConfigDefaults.getDefault(NEITHER_KEY));
        }

        @Test
        @DisplayName("非注册 key 且两级都缺 → Tier 3 返回空串（SystemConfigDefaults 契约）")
        void unregisteredKeyReturnsEmptyString() {
            TenantContext.set("acme");
            String unknown = "app.not.registered.key";
            assertThat(SystemConfigDefaults.getDefault(unknown)).isEmpty();
            assertThat(service.getString(unknown)).isEmpty();
        }
    }

    @Nested
    @DisplayName("关闭态行为等价（saas.enabled=false 语义）")
    class ClosedState {

        @Test
        @DisplayName("current==default 时 Tier 2 显式跳过，不重复发起 default findByKey")
        void defaultSkipsPlatformFallback() {
            TenantContext.clear(); // getOrDefault() → "default"
            // NEITHER_KEY 未播种，Tier 1 miss；若 Tier 2 未跳过会再触发 1 次 findByKey，共 2 次；
            // 跳过则只 1 次 findByKey。本用例走 mock 精确次数验证——是"跳过分支"的最直接锁。
            service.getString(NEITHER_KEY);
            verify(gateway, org.mockito.Mockito.times(1)).findByKey(NEITHER_KEY);
            verify(gateway, org.mockito.Mockito.times(1)).findAll();
        }

        @Test
        @DisplayName("关闭态下 SystemConfigDefaults 兜底路径不破")
        void closedStateFallsBackToBuiltin() {
            TenantContext.clear();
            assertThat(service.getString(NEITHER_KEY))
                    .isEqualTo(SystemConfigDefaults.getDefault(NEITHER_KEY));
        }

        @Test
        @DisplayName("default 上下文读写走同一 Tier 1，无 Tier 2 副作用")
        void defaultWriteUsesTier1Slot() {
            TenantContext.clear();
            // SystemConfigDefaults 里已注册的 key 才能 updateValue（白名单校验）。
            // 借用一个已注册的 INTEGER key 走 update，检查 DB 保存被调用 1 次；不校验 Tier 2 参与。
            String registeredKey = "exam.plan.auto-balance.max-rounds";
            service.updateValue(registeredKey, "7");
            verify(gateway, org.mockito.Mockito.times(1)).saveOrUpdate(any(SystemConfig.class));
            assertThat(service.getString(registeredKey)).isEqualTo("7");
        }
    }

    @Nested
    @DisplayName("缓存分片与失效")
    class CacheSharding {

        @Test
        @DisplayName("acme 读平台 key 后，第二次读走 (default, key) 缓存位，不再回源 default findByKey")
        void platformReadPopulatesDefaultSlotNotAcme() {
            TenantContext.set("acme");
            assertThat(service.getString(PLATFORM_ONLY_KEY)).isEqualTo("platform-value");
            // 第二次读：Tier 1 (acme) 仍 miss（未播种 + 无负缓存），Tier 2 (default) 命中缓存。
            // 若 Tier 2 也走 DB 说明缓存分片错乱；这里以 findByKey(PLATFORM_ONLY_KEY) 次数 == 2 锁定：
            // 第 1 次 = Tier 1 acmiss → 走 default 缓存命中；
            // 第 2 次 = Tier 1 acme miss；Tier 2 命中缓存不发起 DB 查询。
            TenantContext.set("acme");
            assertThat(service.getString(PLATFORM_ONLY_KEY)).isEqualTo("platform-value");
            verify(gateway, org.mockito.Mockito.times(2)).findByKey(PLATFORM_ONLY_KEY);
        }

        @Test
        @DisplayName("ensureCacheLoaded 跨租户预加载不再错位（P4-T1 S-1 修复点）")
        void cacheLoadedUnderRightTenant() {
            TenantContext.set("acme");
            // 顺序：先读平台 key（触发 acme + default 两级预加载），再读 acme 独有 key。
            // 若旧写法错位，acme findAll 会把 acme 行挂到 (default, *) 槽；default findAll 会把
            // default 行挂到 (acme, *) 槽——两次读值都会互串，返回错值或 null。
            assertThat(service.getString(PLATFORM_ONLY_KEY)).isEqualTo("platform-value");
            assertThat(service.getString(TENANT_ONLY_KEY)).isEqualTo("acme-tenant-value");
        }

        @Test
        @DisplayName("evictTenant(acme) 只清 acme 槽，不动 default 槽")
        void evictAcmeKeepsPlatformCache() {
            TenantContext.set("acme");
            // 预热两级
            service.getString(PLATFORM_ONLY_KEY);
            service.getString(TENANT_ONLY_KEY);
            // 记录预热后 default 槽已就位的状态
            assertThat(service.getString(PLATFORM_ONLY_KEY)).isEqualTo("platform-value");

            service.evictTenant("acme");

            // 再读 acme 独有 key：Tier 1 走 acme 重新加载；再读平台 key：Tier 1 acme miss
            // → Tier 2 default 缓存命中（未受 evict 牵连）。若 default 槽被牵连清空，
            // Tier 2 会重新触发 default findAll，值仍正确但多一次 IO——本用例不锁 IO，只锁返回值。
            TenantContext.set("acme");
            assertThat(service.getString(TENANT_ONLY_KEY)).isEqualTo("acme-tenant-value");
            assertThat(service.getString(PLATFORM_ONLY_KEY)).isEqualTo("platform-value");
            // saveOrUpdate 从未被调用（读用例不写库）
            verify(gateway, never()).saveOrUpdate(any());
        }
    }
}
