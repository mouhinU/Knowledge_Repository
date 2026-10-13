package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantUsageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * 租户用量计量 MyBatis-Plus Mapper（P6-T1）。
 *
 * <p>提供 {@link #atomicIncrement} 方法用于并发安全的用量递增。
 */
@Mapper
public interface TenantUsageMapper extends BaseMapper<TenantUsageDO> {

    /**
     * 原子递增，返回受影响行数。0 = 行不存在需 seed。
     *
     * @param tenantId 租户 id
     * @param metric   指标 code
     * @param period   聚合周期
     * @param amount   增量
     * @return 受影响行数
     */
    @Update(
            "UPDATE tenant_usage SET usage_value = usage_value + #{amount}, update_time = NOW() "
                    + "WHERE tenant_id = #{tenantId} AND metric = #{metric} AND period = #{period}")
    int atomicIncrement(
            @Param("tenantId") long tenantId,
            @Param("metric") String metric,
            @Param("period") LocalDate period,
            @Param("amount") long amount);
}
