package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.mouhin.knowledge.repository.domain.gateway.TenantUsageRecorder;
import com.mouhin.knowledge.repository.domain.model.valueobject.UsageMetric;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 用量计量空实现（P6-T1 · 门控关闭时装配）。
 *
 * <p>当 {@code knowledge.saas.quota.enabled=false}（默认）时装配，所有方法为空操作， 保证 {@code TenantUsageRecorder}
 * 端口在任何配置下都有可用 bean。
 */
@Component
@ConditionalOnProperty(
        name = "knowledge.saas.quota.enabled",
        havingValue = "false",
        matchIfMissing = true)
@Slf4j
public class NoopTenantUsageRecorder implements TenantUsageRecorder {

    @Override
    public void record(String tenantKey, UsageMetric metric, long amount) {
        // no-op
    }

    @Override
    public long getCurrentUsage(String tenantKey, UsageMetric metric, LocalDate period) {
        return 0L;
    }
}
