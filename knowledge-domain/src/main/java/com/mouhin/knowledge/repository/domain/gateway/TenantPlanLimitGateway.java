package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.TenantPlanLimit;
import com.mouhin.knowledge.repository.domain.model.valueobject.UsageMetric;
import java.util.List;
import java.util.Optional;

/**
 * 套餐限额仓储端口（P6-T2）。
 *
 * <p>平台管理员 CRUD，租户侧只读。
 */
public interface TenantPlanLimitGateway {

    /**
     * 查询指定租户的全部限额配置。
     *
     * @param tenantKey 归一化租户键
     * @return 限额列表（可能为空）
     */
    List<TenantPlanLimit> findByTenantKey(String tenantKey);

    /**
     * 查询指定租户指定 metric 的限额。无记录返回 empty（= 无约束）。
     *
     * @param tenantKey 归一化租户键
     * @param metric 指标枚举
     * @return 限额（可选）
     */
    Optional<TenantPlanLimit> findByTenantKeyAndMetric(String tenantKey, UsageMetric metric);

    /**
     * 新增或更新限额（upsert 语义）。
     *
     * @param tenantKey 归一化租户键
     * @param limit 限额值对象
     */
    void saveOrUpdate(String tenantKey, TenantPlanLimit limit);

    /**
     * 删除指定租户指定 metric 的限额。
     *
     * @param tenantKey 归一化租户键
     * @param metric 指标枚举
     */
    void delete(String tenantKey, UsageMetric metric);
}
