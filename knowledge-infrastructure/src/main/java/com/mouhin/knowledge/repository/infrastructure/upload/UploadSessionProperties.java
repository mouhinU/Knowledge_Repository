package com.mouhin.knowledge.repository.infrastructure.upload;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上传会话配置属性
 *
 * <p>会话 TTL 同时控制 Redis key 过期与降级模式惰性清理窗口。
 */
@Component
@ConfigurationProperties(prefix = "knowledge.upload.session")
public class UploadSessionProperties {

    /**
     * 会话超时秒数，默认 7200（2 小时）
     */
    private long sessionTtlSeconds = 7200L;

    public long getSessionTtlSeconds() {
        return sessionTtlSeconds;
    }

    public void setSessionTtlSeconds(long sessionTtlSeconds) {
        this.sessionTtlSeconds = sessionTtlSeconds;
    }
}
