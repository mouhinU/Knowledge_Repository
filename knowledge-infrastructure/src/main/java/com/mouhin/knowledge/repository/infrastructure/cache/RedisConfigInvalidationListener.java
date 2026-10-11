package com.mouhin.knowledge.repository.infrastructure.cache;

import com.mouhin.knowledge.repository.domain.event.ConfigCacheInvalidationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis 配置缓存失效消息监听器（P4-T2 · B-1 方案 B）。
 *
 * <p>订阅 {@code kr:config:invalidate} channel，收到消息后通过 Spring {@link ApplicationEventPublisher} 发布
 * {@link ConfigCacheInvalidationEvent} 到应用层，由 {@code SystemConfigService} 的 {@code @EventListener}
 * 方法清除对应租户的进程内缓存。
 *
 * <p>事件解耦设计：infra（Redis 消费端）与 app（缓存持有者）均依赖 domain，事件类放 domain 保证编译可达， 避免 infrastructure →
 * application 的违规反向引用。
 *
 * @author mouhinU
 * @date 2026-10-11
 */
@Configuration
@ConditionalOnProperty(
        name = "knowledge.redis.enabled",
        havingValue = "true",
        matchIfMissing = true)
@Slf4j
public class RedisConfigInvalidationListener {

    @Bean
    public RedisMessageListenerContainer configInvalidationListenerContainer(
            RedisConnectionFactory connectionFactory, ApplicationEventPublisher eventPublisher) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> {
                    String tenantKey = new String(message.getBody());
                    log.debug("[ConfigInvalidation] 收到失效信号: tenant={}", tenantKey);
                    eventPublisher.publishEvent(new ConfigCacheInvalidationEvent(tenantKey));
                },
                new ChannelTopic(RedisConfigInvalidationPublisher.CHANNEL));
        return container;
    }
}
