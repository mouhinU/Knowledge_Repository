package com.mouhin.knowledge.repository.domain.gateway;

/**
 * 配置缓存失效广播端口（P4-T2）。
 *
 * <p>当 {@code sys_config} 发生写入时，由应用层调用 {@link #publish(String, String)} 广播失效信号； 所有实例的 {@code
 * MessageListener} 收到信号后清除对应租户的进程内缓存，保证多实例配置一致性。
 *
 * <p>infra 提供两套实现：
 *
 * <ul>
 *   <li>{@code RedisConfigInvalidationPublisher}（Redis pub/sub，{@code knowledge.redis.enabled=true}
 *       时装配）；
 *   <li>{@code NoopConfigInvalidationPublisher}（缺席降级，仅打 WARN 日志）。
 * </ul>
 *
 * @author mouhinU
 * @date 2026-10-11
 */
@FunctionalInterface
public interface ConfigInvalidationPublisher {

    /**
     * 广播配置失效信号。
     *
     * @param tenantKey 租户业务键
     * @param configKey 配置键（供未来细粒度失效扩展，当前实现按租户级失效）
     */
    void publish(String tenantKey, String configKey);
}
