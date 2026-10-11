package com.mouhin.knowledge.repository.infrastructure.cache;

import com.mouhin.knowledge.repository.domain.gateway.ConfigInvalidationPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis pub/sub 的配置缓存失效广播器（P4-T2 · B-1 方案 B）。
 *
 * <p>装配条件：{@code knowledge.redis.enabled=true}（默认 true）。写入时向 {@code kr:config:invalidate} channel
 * 广播 {@code tenantKey} 载荷，所有订阅实例的 {@link RedisConfigInvalidationListener} 收到信号后通过 Spring 事件机制通知
 * {@code SystemConfigService} 清除对应租户的进程内缓存。
 *
 * <p>Redis 缺席时自动降级到 {@link NoopConfigInvalidationPublisher}（TTL 300s 兜底 + 手工失效端点）。
 *
 * @author mouhinU
 * @date 2026-10-11
 */
@Component
@Slf4j
@ConditionalOnProperty(
        name = "knowledge.redis.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RedisConfigInvalidationPublisher implements ConfigInvalidationPublisher {

    static final String CHANNEL = "kr:config:invalidate";

    private final StringRedisTemplate redisTemplate;

    public RedisConfigInvalidationPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void publish(String tenantKey, String configKey) {
        try {
            redisTemplate.convertAndSend(CHANNEL, tenantKey);
            log.debug("[ConfigInvalidation] 已广播: tenant={}, key={}", tenantKey, configKey);
        } catch (Exception e) {
            log.warn(
                    "[ConfigInvalidation] Redis 广播失败，降级到 TTL 兜底: tenant={}, error={}",
                    tenantKey,
                    e.getMessage());
        }
    }
}
