package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 租户删除步骤数据对象（P3-T5，映射 {@code sys_tenant_deletion_step}）。
 *
 * <p>平台级表，本身不带 {@code tenant_id} 拦截（{@code tenant_id} 是业务字段而非拦截器过滤维度）； 须登记到 {@code
 * TenantLineInnerInterceptor} 的 ignore 名单。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Getter
@Setter
@TableName("sys_tenant_deletion_step")
public class TenantDeletionStepDO {

    @TableId(value = "step_id", type = IdType.AUTO)
    private Long stepId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("deletion_request_id")
    private String deletionRequestId;

    @TableField("step_name")
    private String stepName;

    @TableField("attempt_no")
    private Integer attemptNo;

    @TableField("status")
    private String status;

    @TableField("rows_affected")
    private Long rowsAffected;

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
