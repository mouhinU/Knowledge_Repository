package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 平台端审计日志数据对象（P3-T5，映射 {@code sys_audit_log}，S-4=方案 A 审计落库）。
 *
 * <p>平台级表，{@code tenant_key} 是业务列而非拦截器过滤维度；须登记到 {@code TenantLineInnerInterceptor} 的 ignore 名单。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Getter
@Setter
@TableName("sys_audit_log")
public class AuditLogDO {

    @TableId(value = "audit_id", type = IdType.AUTO)
    private Long auditId;

    @TableField("operator")
    private String operator;

    @TableField("action")
    private String action;

    @TableField("tenant_key")
    private String tenantKey;

    @TableField("outcome")
    private String outcome;

    @TableField("detail")
    private String detail;

    @TableField("create_time")
    private LocalDateTime createTime;
}
