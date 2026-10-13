package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 租户开通步骤数据对象（P3-T2，映射 {@code sys_tenant_provision_step}）。
 *
 * <p>平台级表，本身不带 {@code tenant_id} 拦截（{@code tenant_id} 是业务字段而非拦截器过滤维度）； 须登记到 {@code
 * TenantLineInnerInterceptor} 的 ignore 名单。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Getter
@Setter
@TableName("sys_tenant_provision_step")
public class TenantProvisionStepDO {

    @TableId(value = "step_id", type = IdType.AUTO)
    private Long stepId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("provision_request_id")
    private String provisionRequestId;

    @TableField("step_name")
    private String stepName;

    @TableField("attempt_no")
    private Integer attemptNo;

    @TableField("status")
    private String status;

    @TableField("error_detail")
    private String errorDetail;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
