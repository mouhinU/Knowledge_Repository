package com.mouhin.knowledge.repository.client.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统配置 DTO（client 层契约）
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Getter
@Setter
public class SystemConfigDTO {

    /** 主键 */
    private Long id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 值类型：STRING / INTEGER / BOOLEAN / DECIMAL */
    private String valueType;

    /** 配置说明 */
    private String description;

    /** 分组 */
    private String category;

    /** 是否允许页面编辑 */
    private Boolean editable;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
