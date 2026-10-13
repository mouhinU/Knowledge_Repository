package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.gateway.TenantUsageRecorder;
import com.mouhin.knowledge.repository.domain.model.valueobject.UsageMetric;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantUsageDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.TenantUsageMapper;
import com.mouhin.knowledge.repository.infrastructure.tenant.TenantIdResolver;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * 租户用量计量 JDBC 实现（P6-T1）。
 *
 * <p>采用 "原子 UPDATE → 受影响 0 行则 INSERT seed → DuplicateKey 退化 UPDATE" 三步模式， 兼容 H2（MySQL 兼容模式）与
 * MySQL，无需 {@code ON DUPLICATE KEY UPDATE} 方言差异。
 */
@Repository
@ConditionalOnProperty(name = "knowledge.saas.quota.enabled", havingValue = "true")
@Slf4j
public class TenantUsageRecorderImpl implements TenantUsageRecorder {

    private final TenantUsageMapper mapper;
    private final TenantIdResolver tenantIdResolver;

    public TenantUsageRecorderImpl(TenantUsageMapper mapper, TenantIdResolver tenantIdResolver) {
        this.mapper = mapper;
        this.tenantIdResolver = tenantIdResolver;
    }

    private static LocalDate currentPeriod() {
        return LocalDate.now().withDayOfMonth(1);
    }

    @Override
    public void record(String tenantKey, UsageMetric metric, long amount) {
        if (amount <= 0) {
            return;
        }
        try {
            long tenantId = tenantIdResolver.resolve(tenantKey);
            LocalDate period = currentPeriod();
            int affected = mapper.atomicIncrement(tenantId, metric.code(), period, amount);
            if (affected == 0) {
                seedAndRetry(tenantId, metric, period, amount);
            }
        } catch (Exception e) {
            log.warn(
                    "[usage] record failed tenantKey={} metric={} amount={}: {}",
                    tenantKey,
                    metric,
                    amount,
                    e.getMessage());
        }
    }

    @Override
    public long getCurrentUsage(String tenantKey, UsageMetric metric, LocalDate period) {
        long tenantId = tenantIdResolver.resolve(tenantKey);
        LambdaQueryWrapper<TenantUsageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantUsageDO::getTenantId, tenantId)
                .eq(TenantUsageDO::getMetric, metric.code())
                .eq(TenantUsageDO::getPeriod, period);
        TenantUsageDO row = mapper.selectOne(wrapper);
        return row != null ? row.getUsageValue() : 0L;
    }

    /** 首次 seed：INSERT ... 唯一键冲突则 retry UPDATE。 */
    private void seedAndRetry(long tenantId, UsageMetric metric, LocalDate period, long amount) {
        try {
            TenantUsageDO seed = new TenantUsageDO();
            seed.setTenantId(tenantId);
            seed.setMetric(metric.code());
            seed.setPeriod(period);
            seed.setUsageValue(amount);
            seed.setCreateTime(LocalDateTime.now());
            seed.setUpdateTime(LocalDateTime.now());
            mapper.insert(seed);
        } catch (DuplicateKeyException dup) {
            // 并发 seed → 退化为 UPDATE
            mapper.atomicIncrement(tenantId, metric.code(), period, amount);
        }
    }
}
