package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 系统配置实体（领域层）
 *
 * <p>承载特性开关与运行时可调参数，支持三层获取策略：内存缓存 → 数据库 → 系统默认值。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
public class SystemConfig {

    /** 主键 */
    private Long id;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Boolean getEditable() {
        return editable;
    }

    public void setEditable(Boolean editable) {
        this.editable = editable;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

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
