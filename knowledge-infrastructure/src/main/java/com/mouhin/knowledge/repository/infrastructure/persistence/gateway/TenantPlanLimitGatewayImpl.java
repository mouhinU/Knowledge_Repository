package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.gateway.TenantPlanLimitGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.QuotaPolicy;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantPlanLimit;
import com.mouhin.knowledge.repository.domain.model.valueobject.UsageMetric;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantPlanLimitDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.TenantPlanLimitMapper;
import com.mouhin.knowledge.repository.infrastructure.tenant.TenantIdResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 套餐限额持久化实现（P6-T2）。
 *
 * <p>infra 层负责 DO ↔ 领域值对象转换。
 */
@Component
@Slf4j
public class TenantPlanLimitGatewayImpl implements TenantPlanLimitGateway {

    private final TenantPlanLimitMapper mapper;
    private final TenantIdResolver tenantIdResolver;

    public TenantPlanLimitGatewayImpl(
            TenantPlanLimitMapper mapper, TenantIdResolver tenantIdResolver) {
        this.mapper = mapper;
        this.tenantIdResolver = tenantIdResolver;
    }

    @Override
    public List<TenantPlanLimit> findByTenantKey(String tenantKey) {
        long tenantId = tenantIdResolver.resolve(tenantKey);
        LambdaQueryWrapper<TenantPlanLimitDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantPlanLimitDO::getTenantId, tenantId);
        return mapper.selectList(wrapper).stream()
                .map(TenantPlanLimitGatewayImpl::toDomain)
                .toList();
    }

    @Override
    public Optional<TenantPlanLimit> findByTenantKeyAndMetric(
            String tenantKey, UsageMetric metric) {
        long tenantId = tenantIdResolver.resolve(tenantKey);
        LambdaQueryWrapper<TenantPlanLimitDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantPlanLimitDO::getTenantId, tenantId)
                .eq(TenantPlanLimitDO::getMetric, metric.code());
        TenantPlanLimitDO doObj = mapper.selectOne(wrapper);
        return doObj != null ? Optional.of(toDomain(doObj)) : Optional.empty();
    }

    @Override
    public void saveOrUpdate(String tenantKey, TenantPlanLimit limit) {
        long tenantId = tenantIdResolver.resolve(tenantKey);
        TenantPlanLimitDO existing =
                mapper.selectOne(
                        new LambdaQueryWrapper<TenantPlanLimitDO>()
                                .eq(TenantPlanLimitDO::getTenantId, tenantId)
                                .eq(TenantPlanLimitDO::getMetric, limit.metric().code()));
        if (existing == null) {
            TenantPlanLimitDO doObj = new TenantPlanLimitDO();
            doObj.setTenantId(tenantId);
            doObj.setMetric(limit.metric().code());
            doObj.setQuota(limit.quota());
            doObj.setPolicy(limit.policy().name());
            doObj.setCreateTime(LocalDateTime.now());
            doObj.setUpdateTime(LocalDateTime.now());
            mapper.insert(doObj);
        } else {
            existing.setQuota(limit.quota());
            existing.setPolicy(limit.policy().name());
            existing.setUpdateTime(LocalDateTime.now());
            mapper.updateById(existing);
        }
        log.debug("[PlanLimit] saved: tenantKey={} metric={}", tenantKey, limit.metric());
    }

    @Override
    public void delete(String tenantKey, UsageMetric metric) {
        long tenantId = tenantIdResolver.resolve(tenantKey);
        mapper.delete(
                new LambdaQueryWrapper<TenantPlanLimitDO>()
                        .eq(TenantPlanLimitDO::getTenantId, tenantId)
                        .eq(TenantPlanLimitDO::getMetric, metric.code()));
    }

    private static TenantPlanLimit toDomain(TenantPlanLimitDO doObj) {
        return new TenantPlanLimit(
                UsageMetric.fromCode(doObj.getMetric()),
                doObj.getQuota(),
                QuotaPolicy.valueOf(doObj.getPolicy()));
    }
}
