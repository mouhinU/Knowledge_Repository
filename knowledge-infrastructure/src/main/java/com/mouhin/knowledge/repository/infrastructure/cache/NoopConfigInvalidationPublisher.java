package com.mouhin.knowledge.repository.infrastructure.cache;

import com.mouhin.knowledge.repository.domain.constant.FeatureFlagKeys;
import com.mouhin.knowledge.repository.domain.gateway.ConfigInvalidationPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 配置缓存失效广播器的缺席降级实现（P4-T2 · S-2）。
 *
 * <p>装配条件：{@code knowledge.redis.enabled=false} 或 Redis 依赖缺席。仅打 WARN 日志， 不执行实际广播。此时 {@code
 * SystemConfigService} 依赖 TTL 300s 兜底 + 手工失效端点保证最终一致性。
 *
 * @author mouhinU
 * @date 2026-10-11
 */
@Component
@Slf4j
@ConditionalOnProperty(name = FeatureFlagKeys.REDIS_ENABLED, havingValue = "false")
public class NoopConfigInvalidationPublisher implements ConfigInvalidationPublisher {

    @Override
    public void publish(String tenantKey, String configKey) {
        log.warn(
                "[ConfigInvalidation] Redis 未启用，配置失效广播跳过（降级到 TTL 兜底）: tenant={}, key={}",
                tenantKey,
                configKey);
    }
}
