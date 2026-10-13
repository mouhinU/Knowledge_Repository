package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统配置数据对象
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Getter
@Setter
@TableName("sys_config")
public class SystemConfigDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属租户 id（P3-T3 / C-4=方案 A：提前 P4-T1 租户化 sys_config，V28 加列 NOT NULL DEFAULT 1）。
     *
     * <p>{@code sys_config} 已从 {@code KnowledgeTenantLineHandler.IGNORE_TABLES} 白名单移除，
     * saas.enabled=true 时 MyBatis-Plus 拦截器会自动追加 {@code WHERE tenant_id=?}；双闸门关闭态 由 {@code
     * SystemConfigGatewayImpl} 显式按 {@code TenantContext} 追加。
     */
    @TableField("tenant_id")
    private Long tenantId;

    @TableField("config_key")
    private String configKey;

    @TableField("config_value")
    private String configValue;

    @TableField("value_type")
    private String valueType;

    @TableField("description")
    private String description;

    @TableField("category")
    private String category;

    @TableField("editable")
    private Boolean editable;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
