package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 租户用量计量数据对象（P6-T1）。
 *
 * <p>映射 {@code tenant_usage} 表。每行 = 一个租户一个月一个 metric 的聚合用量。
 */
@Getter
@Setter
@TableName("tenant_usage")
public class TenantUsageDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 id。 */
    private Long tenantId;

    /** 指标 code（对应 {@code UsageMetric.code()}）。 */
    private String metric;

    /** 聚合周期（当月 1 日）。 */
    private LocalDate period;

    /** 累计用量（只增不减）。 */
    private Long usageValue;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
