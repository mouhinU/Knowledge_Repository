package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统配置实体（领域层）
 *
 * <p>承载特性开关与运行时可调参数，支持三层获取策略：内存缓存 → 数据库 → 系统默认值。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Getter
@Setter
public class SystemConfig {

    /** 主键 */
    private Long id;

    /**
     * 所属租户 id（P3-T3 / C-4=方案 A：提前 P4-T1 租户化 sys_config）。
     *
     * <p>与 {@code SystemConfigDO.tenant_id} 一一对应；由 infrastructure 层 {@code SystemConfigGatewayImpl}
     * 依据 {@code TenantContext} + {@code TenantIdResolver} 显式回填，不依赖 MyBatis-Plus 拦截器
     * （saas.enabled=false 双闸门关闭态下拦截器缺席，显式路径确保读写均租户隔离）。
     */
    private Long tenantId;

    /** 配置键（全局唯一） */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 值类型：STRING / INTEGER / BOOLEAN / DECIMAL */
    private String valueType;

    /** 配置说明 */
    private String description;

    /** 分组：feature-toggle / exam / system */
    private String category;

    /** 是否允许页面编辑 */
    private Boolean editable;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    public SystemConfig() {
        // 反射反序列化用
    }

    // ---- getters & setters ----

    // ---- 类型安全的取值方法 ----

    /** 按 valueType 解析为 boolean（仅 BOOLEAN 类型有效） */
    public boolean asBoolean(boolean fallback) {
        if (!"BOOLEAN".equalsIgnoreCase(valueType) || configValue == null) {
            return fallback;
        }
        return Boolean.parseBoolean(configValue.trim());
    }

    /** 按 valueType 解析为 int */
    public int asInt(int fallback) {
        if (configValue == null || configValue.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(configValue.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 按 valueType 解析为 String */
    public String asString(String fallback) {
        return configValue != null ? configValue : fallback;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SystemConfig that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
