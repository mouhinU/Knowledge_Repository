package com.mouhin.knowledge.repository.infrastructure.lock;

import com.mouhin.knowledge.repository.domain.gateway.DistributedLockGateway;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis {@code SET NX EX} 的分布式锁实现（P5-T5 · S-5 方案 A）。
 *
 * <p>装配条件：{@code knowledge.redis.enabled=true}（默认 true）。
 *
 * <p>获锁：{@code SET lockKey ownerId NX EX ttlSeconds}，原子操作，成功即获锁。 释放：Lua 脚本校验 ownerId 匹配后再 {@code
 * DEL}，避免误删他实例的锁（A 获锁 → 超时 → B 获锁 → A 释放误删 B 的场景）。
 *
 * <p>key 命名空间：{@code kr:lock:*}，与 P4-T2 {@code kr:config:*} / P5-T2 {@code kr:upload:*} 隔离。
 *
 * @author mouhinU
 * @date 2026-10-12
 */
@Component
@Slf4j
@ConditionalOnProperty(
        name = "knowledge.redis.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RedisDistributedLockGateway implements DistributedLockGateway {

    /** Lua 脚本：仅当 ownerId 匹配时删除 key（原子操作，防止误删他实例的锁） */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "  return redis.call('del', KEYS[1]) "
                    + "else "
                    + "  return 0 "
                    + "end";

    private static final DefaultRedisScript<Long> UNLOCK_REDIS_SCRIPT = new DefaultRedisScript<>();

    static {
        UNLOCK_REDIS_SCRIPT.setScriptText(UNLOCK_SCRIPT);
        UNLOCK_REDIS_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLockGateway(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(String lockKey, String ownerId, int ttlSeconds) {
        try {
            Boolean acquired =
                    redisTemplate
                            .opsForValue()
                            .setIfAbsent(lockKey, ownerId, Duration.ofSeconds(ttlSeconds));
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("[DistributedLock] 获锁成功: key={}, owner={}", lockKey, ownerId);
                return true;
            }
            log.debug("[DistributedLock] 获锁失败（已被占用）: key={}", lockKey);
            return false;
        } catch (Exception e) {
            log.warn(
                    "[DistributedLock] Redis 操作异常，降级为未获锁: key={}, error={}",
                    lockKey,
                    e.getMessage());
            return false;
        }
    }

    @Override
    public void unlock(String lockKey, String ownerId) {
        try {
            Long released =
                    redisTemplate.execute(
                            UNLOCK_REDIS_SCRIPT,
                            java.util.Collections.singletonList(lockKey),
                            ownerId);
            if (released != null && released == 1L) {
                log.debug("[DistributedLock] 释放锁成功: key={}, owner={}", lockKey, ownerId);
            } else {
                log.debug(
                        "[DistributedLock] 释放锁跳过（ownerId 不匹配或已过期）: key={}, owner={}",
                        lockKey,
                        ownerId);
            }
        } catch (Exception e) {
            log.warn("[DistributedLock] Redis 释放异常: key={}, error={}", lockKey, e.getMessage());
        }
    }
}
