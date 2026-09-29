package com.mouhin.knowledge.repository.infrastructure.tenant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.TenantMapper;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 租户键 → 租户 id 解析器（P1-T7，SaaS 租户脊柱）。
 *
 * <p>{@link com.mouhin.knowledge.repository.domain.context.TenantContext} 里持有的是 HTTP 层解析出的 {@code
 * tenant_key}（字符串，如 {@code default}/{@code acme}），而业务表列 {@code tenant_id BIGINT} 需要的是数值主键。二者之间需要一层
 * key→id 映射，供 {@code TenantLineInnerInterceptor} 在拼 {@code WHERE tenant_id=?} 时使用。
 *
 * <p>缓存策略：{@link ConcurrentHashMap} 懒加载 + miss 时回源 DB。当前阶段（Phase 1）租户数量 ≤ 3 位数，不做 TTL，读到的 id 视作不变；
 * Phase 3 会引入 {@code TenantLifecycleService} 里的显式 {@link #invalidate(String)} 与 {@link #clear()}
 * 钩子， 处理租户删除/重命名场景，避免陈旧映射。
 *
 * <p>不存在或未激活的 key 直接抛 {@link IllegalStateException}——这是"配置/迁移/令牌"三方漂移的哨兵，宁可让请求 500 也不允许静默 fallback
 * 到 {@code default} 而误读写其他租户的数据。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@Component
public class TenantIdResolver {

    private static final Logger log = LoggerFactory.getLogger(TenantIdResolver.class);

    private final TenantMapper tenantMapper;

    private final ConcurrentMap<String, Long> cache = new ConcurrentHashMap<>();

    public TenantIdResolver(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    /**
     * 解析 tenant_key → tenant_id。
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
        Long cached = cache.get(normalized);
        if (cached != null) {
            return cached;
        }
        TenantDO row =
                tenantMapper.selectOne(
                        Wrappers.<TenantDO>lambdaQuery()
                                .eq(TenantDO::getTenantKey, normalized)
                                .last("LIMIT 1"));
        if (row == null) {
            throw new IllegalStateException("Unknown tenant_key: " + normalized);
        }
        if (!TenantDO.STATUS_ACTIVE.equals(row.getStatus())) {
            throw new IllegalStateException(
                    "Tenant " + normalized + " is not ACTIVE, status=" + row.getStatus());
        }
        Long id = row.getTenantId();
        cache.put(normalized, id);
        if (log.isDebugEnabled()) {
            log.debug("Resolved tenant_key={} → tenant_id={}", normalized, id);
        }
        return id;
    }

    /** 显式失效某个 key 的缓存（Phase 3 生命周期钩子使用）。 */
    public void invalidate(String tenantKey) {
        if (tenantKey == null) {
            return;
        }
        cache.remove(tenantKey.trim().toLowerCase(Locale.ROOT));
    }

    /** 全量清空缓存（配置热更 / 测试隔离使用）。 */
    public void clear() {
        cache.clear();
    }
}
