package com.mouhin.knowledge.repository.application.support;

import com.mouhin.knowledge.repository.domain.gateway.SystemConfigGateway;
import com.mouhin.knowledge.repository.domain.model.entity.SystemConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 系统配置服务 — 三层获取策略
 *
 * <p>优先级：内存缓存（ConcurrentHashMap） → 数据库（sys_config） → 系统默认值（{@link SystemConfigDefaults}）。
 *
 * <p>写入时同步刷新缓存，保证后续读取立即生效。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
@Slf4j
public class SystemConfigService {

    private final SystemConfigGateway systemConfigGateway;

    /** 内存缓存：configKey → SystemConfig */
    private final Map<String, SystemConfig> cache = new ConcurrentHashMap<>();

    /** 缓存是否已全量加载 */
    private volatile boolean cacheLoaded;

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
        ensureCacheLoaded();
        // Tier 1: 内存缓存
        SystemConfig cached = cache.get(configKey);
        if (cached != null) {
            return Optional.of(cached);
        }
        // Tier 2: 数据库
        Optional<SystemConfig> fromDb = systemConfigGateway.findByKey(configKey);
        fromDb.ifPresent(e -> cache.put(configKey, e));
        return fromDb;
    }

    // ---- 管理页面用 ----

    /** 查询全部配置（列表页） */
    public List<SystemConfig> listAll() {
        ensureCacheLoaded();
        List<SystemConfig> fromDb = systemConfigGateway.findAll();
        for (SystemConfig c : fromDb) {
            cache.put(c.getConfigKey(), c);
        }
        return fromDb;
    }

    /** 更新配置值（管理页面保存） */
    public void updateValue(String configKey, String newValue) {
        if (!SystemConfigDefaults.getAllDefaults().containsKey(configKey)) {
            throw new IllegalArgumentException("Unknown system config key");
        }
        Optional<SystemConfig> existing = systemConfigGateway.findByKey(configKey);
        if (existing.isPresent()) {
            SystemConfig config = existing.get();
            if (!Boolean.TRUE.equals(config.getEditable())) {
                throw new IllegalStateException("System config is read-only");
            }
            config.setConfigValue(newValue);
            systemConfigGateway.saveOrUpdate(config);
            cache.put(configKey, config);
        } else {
            // 数据库中不存在时，仅为已注册的默认配置创建记录。
            SystemConfig config = new SystemConfig();
            config.setConfigKey(configKey);
            config.setConfigValue(newValue);
            config.setValueType(SystemConfigDefaults.getValueType(configKey));
            config.setDescription(SystemConfigDefaults.getDescription(configKey));
            config.setCategory(SystemConfigDefaults.getCategory(configKey));
            config.setEditable(true);
            systemConfigGateway.saveOrUpdate(config);
            cache.put(configKey, config);
        }
        log.info("[SystemConfig] 配置已更新: {}", configKey);
    }

    /** 手动刷新缓存（预留管理接口） */
    public void refreshCache() {
        cache.clear();
        cacheLoaded = false;
        ensureCacheLoaded();
        log.info("[SystemConfig] 缓存已刷新，共 {} 条", cache.size());
    }

    // ---- 内部 ----

    private void ensureCacheLoaded() {
        if (!cacheLoaded) {
            synchronized (this) {
                if (!cacheLoaded) {
                    List<SystemConfig> all = systemConfigGateway.findAll();
                    for (SystemConfig c : all) {
                        cache.put(c.getConfigKey(), c);
                    }
                    cacheLoaded = true;
                    log.debug("[SystemConfig] 缓存全量加载完成，共 {} 条", cache.size());
                }
            }
        }
    }
}
