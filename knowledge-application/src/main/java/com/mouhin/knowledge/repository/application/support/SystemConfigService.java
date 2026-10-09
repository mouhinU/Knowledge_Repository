package com.mouhin.knowledge.repository.application.support;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.gateway.SystemConfigGateway;
import com.mouhin.knowledge.repository.domain.model.entity.SystemConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 系统配置服务 — 三层获取策略（P3-T3 起按租户隔离缓存）
 *
 * <p>优先级：内存缓存（ConcurrentHashMap） → 数据库（sys_config，V28 起含 tenant_id 列） → 系统默认值（{@link
 * SystemConfigDefaults}）。
 *
 * <p><b>租户隔离</b>：缓存 key 为 {@code tenantKey + ":" + configKey}；{@code loadedTenants} 记录哪些租户已全量加载，避免
 * A 租户命中 B 租户遗留缓存。所有读写方法一律以 {@link TenantContext#getOrDefault()} 解析当前租户；写入时同步刷新对应租户缓存。 {@code
 * refreshCache()} 为管理动作，一次性清空所有租户缓存并回落到"下次访问按需重建"。
 *
 * <p>Gateway 层（{@code SystemConfigGatewayImpl}）已按 {@code tenant_id} 显式过滤，本服务的缓存隔离与其保持一致的租户口径。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
@Slf4j
public class SystemConfigService {

    /** 缓存 key 分隔符（tenantKey 与 configKey 均不含冒号，安全可逆）。 */
    private static final String CACHE_KEY_SEP = ":";

    private final SystemConfigGateway systemConfigGateway;

    /** 内存缓存：{@code tenantKey:configKey} → SystemConfig */
    private final Map<String, SystemConfig> cache = new ConcurrentHashMap<>();

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

    /** 获取配置实体（含元数据），供管理页面使用 */
    public Optional<SystemConfig> getEntity(String configKey) {
        String tenantKey = TenantContext.getOrDefault();
        ensureCacheLoaded(tenantKey);
        String cacheKey = cacheKey(tenantKey, configKey);
        // Tier 1: 内存缓存
        SystemConfig cached = cache.get(cacheKey);
        if (cached != null) {
            return Optional.of(cached);
        }
        // Tier 2: 数据库（Gateway 已按 tenant_id 过滤）
        Optional<SystemConfig> fromDb = systemConfigGateway.findByKey(configKey);
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
            cache.put(cacheKey(tenantKey, c.getConfigKey()), c);
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
            cache.put(cacheKey(tenantKey, configKey), config);
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
            cache.put(cacheKey(tenantKey, configKey), config);
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
        String prefix = tenantKey + CACHE_KEY_SEP;
        cache.keySet().removeIf(k -> k.startsWith(prefix));
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
            List<SystemConfig> all = systemConfigGateway.findAll();
            for (SystemConfig c : all) {
                cache.put(cacheKey(tenantKey, c.getConfigKey()), c);
            }
            loadedTenants.add(tenantKey);
            log.debug("[SystemConfig] 租户缓存加载完成: tenant={}, entries={}", tenantKey, all.size());
        }
    }

    private static String cacheKey(String tenantKey, String configKey) {
        return tenantKey + CACHE_KEY_SEP + configKey;
    }
}
