package com.mouhin.knowledge.repository.domain.gateway;

/**
 * 分布式锁端口（domain 定义，infrastructure 以 Redis / Noop 实现）。
 *
 * <p>P5-T5 多副本部署下 {@code @Scheduled} 方法防重复执行：同一时刻只有一个副本持有锁。 实现方须保证：
 *
 * <ul>
 *   <li>{@link #tryLock} 原子性——Redis {@code SET key NX EX} 或等价原语；
 *   <li>锁自动过期——防止持锁实例崩溃后死锁，TTL 应大于任务最大执行时间 + buffer；
 *   <li>{@link #unlock} 仅释放自己持有的锁——通过唯一 ownerId 校验，避免误删他实例的锁。
 * </ul>
 *
 * <p>Redis 不可用时降级到 {@code NoopDistributedLockGateway}（进程内 {@code ConcurrentHashMap} 占位，
 * 仅保证单实例不重入，多副本场景退化为"都可能跑"——由业务层 CAS 兜底）。
 *
 * @author mouhinU
 * @date 2026-10-12
 */
public interface DistributedLockGateway {

    /**
     * 尝试获取分布式锁。
     *
     * @param lockKey 锁标识（如 {@code kr:lock:exam-grading}）
     * @param ownerId 持锁者唯一标识（如实例 ID / UUID），用于安全释放
     * @param ttlSeconds 锁自动过期时间（秒），须大于任务最大执行时间
     * @return true=获锁成功；false=已被其他实例持有
     */
    boolean tryLock(String lockKey, String ownerId, int ttlSeconds);

    /**
     * 释放分布式锁。仅释放自己持有的锁（ownerId 匹配）。
     *
     * @param lockKey 锁标识
     * @param ownerId 持锁者标识（须与 {@link #tryLock} 时一致）
     */
    void unlock(String lockKey, String ownerId);
}
