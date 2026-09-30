package com.mouhin.knowledge.repository.infrastructure.tenant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.TenantMapper;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 租户键 ↔ 租户 id 双向解析器（P1-T7 + P1-T8，SaaS 租户脊柱）。
 *
 * <p>{@link com.mouhin.knowledge.repository.domain.context.TenantContext} 里持有的是 HTTP 层解析出的 {@code
 * tenant_key}（字符串，如 {@code default}/{@code acme}），而业务表列 {@code tenant_id BIGINT}
 * 需要的是数值主键。二者之间需要一层双向映射：
 *
 * <ul>
 *   <li>key→id（{@link #resolve(String)}）：{@code TenantLineInnerInterceptor} 拼 {@code WHERE
 *       tenant_id=?} 时使用。
 *   <li>id→key（{@link #lookupKey(long)} / {@link #lookupKeyOrDefault(long, String)}）：{@code
 *       UserGatewayImpl} 从 {@code UserDO.tenantId} 还原 {@code User.tenantKey}、{@code
 *       AdminLoginCmdExe} 写入 JWT claim 时使用。
 * </ul>
 *
 * <p>缓存策略：两张 {@link ConcurrentHashMap} 各自懒加载 + miss 时回源 DB，且每次成功的解析都会<b>同时</b>预热正向与反向缓存，
 * 保证两向查询共享同一份事实。当前阶段（Phase 1）租户数量 ≤ 3 位数，不做 TTL，读到的映射视作不变；Phase 3 会引入 {@code
 * TenantLifecycleService} 里的显式 {@link #invalidate(String)} / {@link #invalidateById(long)} 与 {@link
 * #clear()} 钩子， 处理租户删除/重命名场景，避免陈旧映射。
 *
 * <p>不存在或未激活的 key/id 直接抛 {@link IllegalStateException}——这是"配置/迁移/令牌"三方漂移的哨兵，宁可让请求 500 也不允许静默
 * fallback 到 {@code default} 而误读写其他租户的数据。仅在明确允许"未知即回退"的兼容路径（如旧 DB 行 tenant_id 缺失）才使用 {@link
 * #lookupKeyOrDefault}。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@Component
public class TenantIdResolver {

    private static final Logger log = LoggerFactory.getLogger(TenantIdResolver.class);

    private final TenantMapper tenantMapper;

    private final ConcurrentMap<String, Long> keyToId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> idToKey = new ConcurrentHashMap<>();

    public TenantIdResolver(@Lazy TenantMapper tenantMapper) {
        // @Lazy：TenantMapper→SqlSessionFactory→MybatisPlusAutoConfiguration 会回收集
        // mybatisPlusInterceptor
        // 而该 interceptor 又依赖本 resolver，构成构造期循环（saas.enabled=true 才触发，P1-T10 首次暴露）。
        // 延迟解析 mapper，本 bean 得以先行构造，首次 resolve()/lookupKey() 时再拿到真实 mapper。
        this.tenantMapper = tenantMapper;
    }

    /**
     * 解析 tenant_key → tenant_id（正向）。
     *
     * @param tenantKey 非空非 blank 的租户键（区分大小写按库 collation，本项目使用 utf8mb4_general_ci，等价忽略大小写）
     * @return 对应的数值主键
     * @throws IllegalStateException 未知或已停用租户
     */
    public long resolve(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            throw new IllegalStateException("tenantKey must not be null/blank");
        }
        String normalized = tenantKey.trim().toLowerCase(Locale.ROOT);
        Long cached = keyToId.get(normalized);
        if (cached != null) {
            return cached;
        }
        TenantDO row = warm(loadByKey(normalized));
        return row.getTenantId();
    }

    /**
     * 反查 tenant_id → tenant_key（严格：未知 id 抛异常）。
     *
     * @param tenantId 数据库列 {@code tenant_id} 的数值主键
     * @return 对应的小写 key
     * @throws IllegalStateException 未知或已停用租户
     */
    public String lookupKey(long tenantId) {
        String cached = idToKey.get(tenantId);
        if (cached != null) {
            return cached;
        }
        TenantDO row = warm(loadById(tenantId));
        return canonicalKey(row);
    }

    /**
     * 反查 tenant_id → tenant_key（宽松：未知 id 返回 {@code fallback}，不抛）。 专用于历史 DB 行可能缺 tenant_id
     * 或映射漂移的兼容路径（例如 converter 处理 P1-T6 之前的旧记录）。
     *
     * @param tenantId 数值主键
     * @param fallback 找不到时返回的兜底 key（通常传 {@code TenantContext.DEFAULT_TENANT_KEY}）
     * @return 已知的 key 或 {@code fallback}
     */
    public String lookupKeyOrDefault(long tenantId, String fallback) {
        try {
            return lookupKey(tenantId);
        } catch (IllegalStateException e) {
            if (log.isDebugEnabled()) {
                log.debug("lookupKeyOrDefault miss tenantId={} → fallback={}", tenantId, fallback);
            }
            return fallback;
        }
    }

    private TenantDO loadByKey(String normalizedKey) {
        TenantDO row =
                tenantMapper.selectOne(
                        Wrappers.<TenantDO>lambdaQuery()
                                .eq(TenantDO::getTenantKey, normalizedKey)
                                .last("LIMIT 1"));
        if (row == null) {
            throw new IllegalStateException("Unknown tenant_key: " + normalizedKey);
        }
        return row;
    }

    private TenantDO loadById(long tenantId) {
        TenantDO row = tenantMapper.selectById(tenantId);
        if (row == null) {
            throw new IllegalStateException("Unknown tenant_id: " + tenantId);
        }
        return row;
    }

    private TenantDO warm(TenantDO row) {
        if (!TenantDO.STATUS_ACTIVE.equals(row.getStatus())) {
            throw new IllegalStateException(
                    "Tenant " + row.getTenantKey() + " is not ACTIVE, status=" + row.getStatus());
        }
        Long id = row.getTenantId();
        String canonicalKey = canonicalKey(row);
        keyToId.put(canonicalKey, id);
        idToKey.put(id, canonicalKey);
        if (log.isDebugEnabled()) {
            log.debug("Resolved tenant_key={} ↔ tenant_id={}", canonicalKey, id);
        }
        return row;
    }

    private static String canonicalKey(TenantDO row) {
        return row.getTenantKey().trim().toLowerCase(Locale.ROOT);
    }

    /** 显式失效某个 key 的双向缓存（Phase 3 生命周期钩子使用）。 */
    public void invalidate(String tenantKey) {
        if (tenantKey == null) {
            return;
        }
        String normalized = tenantKey.trim().toLowerCase(Locale.ROOT);
        Long id = keyToId.remove(normalized);
        if (id != null) {
            idToKey.remove(id);
        }
    }

    /** 显式失效某个 id 的双向缓存（Phase 3 生命周期钩子使用）。 */
    public void invalidateById(long tenantId) {
        String key = idToKey.remove(tenantId);
        if (key != null) {
            keyToId.remove(key);
        }
    }

    /** 全量清空双向缓存（配置热更 / 测试隔离使用）。 */
    public void clear() {
        keyToId.clear();
        idToKey.clear();
    }
}
