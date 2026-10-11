package com.mouhin.knowledge.repository.domain.event;

/**
 * 租户配置缓存失效领域事件（P4-T2）。
 *
 * <p>由 infra 层的 Redis {@code MessageListener} 发布到 Spring {@link
 * org.springframework.context.ApplicationEventPublisher}， 应用层 {@code SystemConfigService} 通过
 * {@code @EventListener} 监听并清除对应租户的进程内缓存。
 *
 * <p>该事件解耦了 infra（Redis 消费端）与 app（缓存持有者）的编译期依赖——两者均依赖 domain， 事件类放 domain 保证编译可达，同时避免
 * infrastructure → application 的违规反向引用。
 *
 * @author mouhinU
 * @date 2026-10-11
 */
public class ConfigCacheInvalidationEvent {

    private final String tenantKey;

    public ConfigCacheInvalidationEvent(String tenantKey) {
        this.tenantKey = tenantKey;
    }

    public String tenantKey() {
        return tenantKey;
    }
}
