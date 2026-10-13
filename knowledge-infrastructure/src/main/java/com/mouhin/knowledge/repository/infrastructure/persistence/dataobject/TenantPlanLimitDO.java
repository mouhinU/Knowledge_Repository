package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 套餐限额数据对象（P6-T2）。
 *
 * <p>映射 {@code tenant_plan_limit} 表。每行 = 一个租户一个 metric 的限额配置。
 */
@Getter
@Setter
@TableName("tenant_plan_limit")
public class TenantPlanLimitDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属租户 id。
     */
    private Long tenantId;

    /**
     * 指标 code（对应 {@code UsageMetric.code()}）。
     */
    private String metric;

    /**
     * 限额绝对值（-1 = 无限）。
     */
    private Long quota;

    /**
     * 超限策略：BLOCK / THROTTLE / ALERT。
     */
    private String policy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
