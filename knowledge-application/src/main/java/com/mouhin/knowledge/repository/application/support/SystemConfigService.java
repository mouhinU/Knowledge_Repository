package com.mouhin.knowledge.repository.application.support;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.gateway.SystemConfigGateway;
import com.mouhin.knowledge.repository.domain.model.entity.SystemConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.ConfigCacheKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 系统配置服务 — 三级读取顺序 + 进程内缓存分片（P4-T1 · S-1 定案方案 A）。
 *
 * <p><b>三级读取顺序</b>（{@link #getEntity(String)} / {@link #getString(String)}）：
 *
 * <ol>
 *   <li><b>Tier 1 · 租户级</b>：{@code sys_config WHERE tenant_id = <当前租户>}——由 {@link
 *       SystemConfigGateway#findByKey} 在 {@code TenantContext} 下自然解析；
 *   <li><b>Tier 2 · 平台级</b>：{@code sys_config WHERE tenant_id = 1 (default 租户)}——非 default 租户 在
 *       Tier 1 miss 后回退查平台层。承载"平台运营预置但尚未拷贝到本租户"的配置（如 default 新增后 未走 {@code
 *       CopyDefaultSystemConfigStep} 重新播种的场景）；
 *   <li><b>Tier 3 · 内置默认</b>：{@link SystemConfigDefaults#getDefault(String)}——Java 侧出厂值 注册表，DB 两级全
 *       miss 时兜底，永不返回 {@code null}（{@code getString} 语义）。
 * </ol>
 *
 * <p><b>缓存分片</b>：进程内 {@link ConcurrentHashMap} 以 {@link ConfigCacheKey}{@code (tenantKey,
 * configKey)} 值对象为键，三级共用一套 map 但按 {@code tenantKey} 天然分片——Tier 1 落 {@code (current, key)}，Tier 2 落
 * {@code (default, key)}，各自独立命中/回填。{@code loadedTenants} 记录哪些租户已全量预加载，避免 A 租户命中 B 租户遗留缓存。所有读写方法一律以
 * {@link TenantContext#getOrDefault()} 解析当前租户；写入时同步刷新对应租户缓存。{@code refreshCache()}
 * 为管理动作，一次性清空所有租户缓存并回落到"下次访问按需重建"；{@code evictTenant(tenantKey)} 按 {@code tenantKey}
 * 精确清除指定租户的所有条目（P4-T1 从旧的 {@code startsWith} 前缀匹配修复而来，兄弟前缀污染 bug 已闭）。
 *
 * <p><b>关闭态（{@code saas.enabled=false}）行为等价</b>：{@link TenantContext#getOrDefault()} 恒返回 {@code
 * "default"}，Tier 1 即平台级，{@link #getEntity(String)} 显式跳过 Tier 2 冗余查询——与 v0.2 之前的"cache → DB →
 * 内置"两级完全等价，不引入新读路径。真正的三级分离在 {@code saas.enabled=true} 打开后自然生效。
 *
 * <p><b>ensureCacheLoaded 显式作用域（本次同步修复）</b>：旧写法直接 {@code systemConfigGateway.findAll()} 走当前 {@code
 * TenantContext} 的租户，若被跨租户调用（如 Tier 2 平台级预加载）会把 acme 行错挂到 {@code (default, *)} 缓存位。P4-T1 S-1 起，加载走
 * {@link TenantContext#callWithTenant} 显式包裹， {@code tenantKey} 参数真正驱动 DB 作用域，跨租户预加载语义正确。
 *
 * <p>Gateway 层（{@code SystemConfigGatewayImpl}）已按 {@code tenant_id} 显式过滤，本服务的缓存隔离与其保持一致的租户口径。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
@Slf4j
public class SystemConfigService {

    /**
     * 平台级租户键（sys_config 里充当"平台默认配置"承载体的租户）。
     *
     * <p>与 V23 种子 {@code sys_tenant.tenant_key='default'} / {@code tenant_id=1} 一一对应； P3-T3 {@code
     * copyAllFromTenantToTenant(1L, newTenantId)} 亦把 default 视作新租户开通时的模板。Phase 4 S-1 定案方案 A："平台级 =
     * default 租户"，不引入 {@code tenant_id=0} 哨兵或独立 {@code is_platform} 列，避免与 P1-T7 {@code
     * IGNORE_TABLES} 白名单和红线 #7（domain 保持纯净）摩擦。
     */
    private static final String PLATFORM_TENANT_KEY = TenantContext.DEFAULT_TENANT_KEY;

    private final SystemConfigGateway systemConfigGateway;

    /** 内存缓存：{@link ConfigCacheKey}{@code (tenantKey, configKey)} → SystemConfig */
    private final Map<ConfigCacheKey, SystemConfig> cache = new ConcurrentHashMap<>();

    /** 已全量加载的租户集合（取代旧的全局 cacheLoaded 布尔位）。 */
    private final Set<String> loadedTenants = ConcurrentHashMap.newKeySet();

    public SystemConfigService(SystemConfigGateway systemConfigGateway) {
        this.systemConfigGateway = systemConfigGateway;
    }

    // ---- 读取（三层降级） ----

    /** 获取配置值字符串，三级降级：缓存 → DB → 默认值 */
    public String getString(String configKey) {
        return getEntity(configKey)
                .map(SystemConfig::getConfigValue)
                .orElse(SystemConfigDefaults.getDefault(configKey));
    }

    /** 获取 boolean 配置 */
    public boolean getBoolean(String configKey) {
        String val = getString(configKey);
        return Boolean.parseBoolean(val);
    }

    /** 获取 int 配置 */
    public int getInt(String configKey) {
        String val = getString(configKey);
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return SystemConfigDefaults.getDefaultInt(configKey);
        }
    }

    /**
     * 获取配置实体（含元数据），供管理页面使用；三级读取顺序 Tier 1 + Tier 2。
     *
     * <p>Tier 3（内置默认）由 {@link #getString(String)} 通过 {@code
     * orElse(SystemConfigDefaults.getDefault)} 完成——本方法返回 {@link
     * Optional}，语义上不含"值"字段以外的信息，因此内置层无法构造实体，交由上层从 {@link SystemConfigDefaults} 拿纯字符串。管理页面读到的 {@code
     * listAll()} 只列 Tier 1（当前租户已写入的 sys_config 行），不合并 Tier 2/3——保持"页面上显示什么就是什么"的透明度。
     *
     * @param configKey 配置键
     * @return Tier 1 或 Tier 2 命中的实体；两级均 miss 返回 {@link Optional#empty()}
     */
    public Optional<SystemConfig> getEntity(String configKey) {
        String tenantKey = TenantContext.getOrDefault();
        // Tier 1: 当前租户 sys_config
        Optional<SystemConfig> tenantLevel = lookupUnderTenant(tenantKey, configKey);
        if (tenantLevel.isPresent()) {
            return tenantLevel;
        }
        // Tier 2: 平台级 default sys_config（saas.enabled=false 或 current==default 时 Tier 1 已覆盖，跳过冗余）
        if (PLATFORM_TENANT_KEY.equals(tenantKey)) {
            return Optional.empty();
        }
        return lookupUnderTenant(PLATFORM_TENANT_KEY, configKey);
    }

    /**
     * 按指定租户（缓存 + DB 双层）查找 configKey；DB 查找走 {@link TenantContext#callWithTenant} 显式 隔离作用域，保证 {@code
     * tenantKey} 参数真正驱动 Gateway 层的 {@code WHERE tenant_id=?} 谓词。
     *
     * <p>P4-T1 S-1 起：跨租户读取（Tier 2 平台级回退）必须走本方法，而不是直接 {@code
     * systemConfigGateway.findByKey}——后者读的是当前线程 {@code TenantContext}，会串味。
     *
     * @param tenantKey 目标租户业务键
     * @param configKey 配置键
     * @return 该租户下命中的实体；DB 与缓存均 miss 返回 {@link Optional#empty()}
     */
    private Optional<SystemConfig> lookupUnderTenant(String tenantKey, String configKey) {
        ensureCacheLoaded(tenantKey);
        ConfigCacheKey cacheKey = ConfigCacheKey.of(tenantKey, configKey);
        // Tier .a: 内存缓存
        SystemConfig cached = cache.get(cacheKey);
        if (cached != null) {
            return Optional.of(cached);
        }
        // Tier .b: DB（Gateway 依当前 TenantContext 追加 WHERE tenant_id=?）
        Optional<SystemConfig> fromDb =
                TenantContext.callWithTenant(
                        tenantKey, () -> systemConfigGateway.findByKey(configKey));
        fromDb.ifPresent(e -> cache.put(cacheKey, e));
        return fromDb;
    }

    // ---- 管理页面用 ----

    /** 查询当前租户全部配置（列表页） */
    public List<SystemConfig> listAll() {
        String tenantKey = TenantContext.getOrDefault();
        ensureCacheLoaded(tenantKey);
        List<SystemConfig> fromDb = systemConfigGateway.findAll();
        for (SystemConfig c : fromDb) {
            cache.put(ConfigCacheKey.of(tenantKey, c.getConfigKey()), c);
        }
        return fromDb;
    }

    /** 更新当前租户配置值（管理页面保存） */
    public void updateValue(String configKey, String newValue) {
        if (!SystemConfigDefaults.getAllDefaults().containsKey(configKey)) {
            throw new IllegalArgumentException("Unknown system config key");
        }
        String tenantKey = TenantContext.getOrDefault();
        Optional<SystemConfig> existing = systemConfigGateway.findByKey(configKey);
        if (existing.isPresent()) {
            SystemConfig config = existing.get();
            if (!Boolean.TRUE.equals(config.getEditable())) {
                throw new IllegalStateException("System config is read-only");
            }
            config.setConfigValue(newValue);
            systemConfigGateway.saveOrUpdate(config);
            cache.put(ConfigCacheKey.of(tenantKey, configKey), config);
        } else {
            // 数据库中不存在时，仅为已注册的默认配置创建记录（Gateway 会强制打上当前 tenant_id）。
            SystemConfig config = new SystemConfig();
            config.setConfigKey(configKey);
            config.setConfigValue(newValue);
            config.setValueType(SystemConfigDefaults.getValueType(configKey));
            config.setDescription(SystemConfigDefaults.getDescription(configKey));
            config.setCategory(SystemConfigDefaults.getCategory(configKey));
            config.setEditable(true);
            systemConfigGateway.saveOrUpdate(config);
            cache.put(ConfigCacheKey.of(tenantKey, configKey), config);
        }
        log.info("[SystemConfig] 配置已更新: tenant={}, key={}", tenantKey, configKey);
    }

    /** 手动刷新缓存（管理动作，跨所有租户一次性清空并触发下次按需重建）。 */
    public void refreshCache() {
        cache.clear();
        loadedTenants.clear();
        log.info("[SystemConfig] 全部租户缓存已清空，下次访问按需重建");
    }

    /** 清空指定租户的缓存（供租户 SUSPEND/RESUME/DELETE 生命周期钩子调用，P3-T4 起接入）。 */
    public void evictTenant(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            return;
        }
        cache.keySet().removeIf(k -> k.tenantKey().equals(tenantKey));
        loadedTenants.remove(tenantKey);
        log.info("[SystemConfig] 已清空租户缓存: tenant={}", tenantKey);
    }

    // ---- 内部 ----

    private void ensureCacheLoaded(String tenantKey) {
        if (loadedTenants.contains(tenantKey)) {
            return;
        }
        synchronized (this) {
            if (loadedTenants.contains(tenantKey)) {
                return;
            }
            // P4-T1 S-1 修复：本方法可能被跨租户调用（Tier 2 平台级预加载 default，而当前线程 TenantContext
            // 绑定的可能是 acme/beta），Gateway.findAll() 依当前 TenantContext 追加 WHERE tenant_id=?——
            // 若不显式切租户，会把 acme 行错挂到 (default, *) 缓存位。callWithTenant 保证 tenantKey 参数
            // 真正驱动 DB 作用域，与 §0 三级读取语义一致。
            List<SystemConfig> all =
                    TenantContext.callWithTenant(tenantKey, () -> systemConfigGateway.findAll());
            for (SystemConfig c : all) {
                cache.put(ConfigCacheKey.of(tenantKey, c.getConfigKey()), c);
            }
            loadedTenants.add(tenantKey);
            log.debug("[SystemConfig] 租户缓存加载完成: tenant={}, entries={}", tenantKey, all.size());
        }
    }
}
