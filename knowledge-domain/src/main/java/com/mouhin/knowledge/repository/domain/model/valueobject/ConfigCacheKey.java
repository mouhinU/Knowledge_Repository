package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 系统配置内存缓存键（值对象，P4-T1）。
 *
 * <p>{@code SystemConfigService} 的进程内缓存以「租户 + 配置键」二元组唯一标识一条配置。此前用 {@code tenantKey + ":" +
 * configKey} 字符串拼接作 map key，依赖"两个组成段都不含冒号"这一隐含约定才可逆、且 前缀失效（{@code evictTenant} 用 {@code
 * startsWith}）存在 {@code acme} 误配 {@code acme2} 的脆弱性。本值 对象把该二元组收敛为类型安全的 key，靠 record 的 {@code
 * equals}/{@code hashCode} 天然表达同一性，失效改按 {@code tenantKey} 精确匹配，彻底去掉分隔符与字符串前缀的隐患。
 *
 * <p><b>分层落点（Phase 4 清单 T-2）</b>：与既有 {@link ExtractionCacheKey} 同置于 domain 值对象层，令 {@code
 * SystemConfigService}（app）仅依赖 domain、符合 COLA 依赖倒置。此处仅承载"缓存 key 结构"这一纯机械抽象；「租户级 → 平台级 →
 * 内置默认」读取顺序中的"平台级"层语义留待 Phase 4 会审（S-1）定夺，本对象不预设第三层。
 *
 * @param tenantKey 租户键（不可空，通常取 {@code TenantContext.getOrDefault()}）
 * @param configKey 配置键（不可空，对应 {@code sys_config.config_key}）
 * @author mouhinU
 * @date 2026-10-10
 */
public record ConfigCacheKey(String tenantKey, String configKey) {

    public ConfigCacheKey {
        tenantKey = require(tenantKey, "tenantKey");
        configKey = require(configKey, "configKey");
    }

    /**
     * 工厂方法，语义等同规范构造器。
     *
     * @param tenantKey 租户键
     * @param configKey 配置键
     * @return 缓存键值对象
     */
    public static ConfigCacheKey of(String tenantKey, String configKey) {
        return new ConfigCacheKey(tenantKey, configKey);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ConfigCacheKey." + field + " must not be blank");
        }
        return value;
    }
}
