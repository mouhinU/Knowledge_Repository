package com.mouhin.knowledge.repository.infrastructure.lock;

import com.mouhin.knowledge.repository.domain.constant.FeatureFlagKeys;
import com.mouhin.knowledge.repository.domain.gateway.DistributedLockGateway;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 进程内分布式锁降级实现（Redis 不可用时的兜底）。
 *
 * <p>装配条件：{@code knowledge.redis.enabled=false}。
 *
 * <p>使用 {@link ConcurrentHashMap} 模拟锁：仅保证单 JVM 内不重入，多副本场景退化为"都可能跑"。 真正的防重由业务层 CAS（如 {@code
 * SUBMITTED→GRADING} 认领）兜底。
 *
 * @author mouhinU
 * @date 2026-10-12
 */
@Component
@Slf4j
@ConditionalOnProperty(name = FeatureFlagKeys.REDIS_ENABLED, havingValue = "false")
public class NoopDistributedLockGateway implements DistributedLockGateway {

    /** 进程内锁表：lockKey → ownerId */
    private final ConcurrentHashMap<String, String> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String lockKey, String ownerId, int ttlSeconds) {
        String existing = locks.putIfAbsent(lockKey, ownerId);
        if (existing == null) {
            log.debug("[NoopLock] 进程内获锁: key={}, owner={}", lockKey, ownerId);
            return true;
        }
        if (existing.equals(ownerId)) {
            // 同一 ownerId 重入，允许
            log.debug("[NoopLock] 进程内重入: key={}, owner={}", lockKey, ownerId);
            return true;
        }
        log.debug("[NoopLock] 进程内获锁失败（已被占用）: key={}", lockKey);
        return false;
    }

    @Override
    public void unlock(String lockKey, String ownerId) {
        locks.remove(lockKey, ownerId);
        log.debug("[NoopLock] 进程内释放锁: key={}, owner={}", lockKey, ownerId);
    }
}
