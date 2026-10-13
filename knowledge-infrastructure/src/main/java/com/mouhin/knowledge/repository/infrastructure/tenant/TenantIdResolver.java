package com.mouhin.knowledge.repository.infrastructure.tenant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mouhin.knowledge.repository.domain.gateway.TenantDirectory;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.TenantMapper;
import java.util.Locale;
import java.util.Optional;
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
 * <p>缓存策略：{@link ConcurrentHashMap} 各自懒加载 + miss 时回源 DB，且每次成功的解析都会<b>同时</b>预热正向与反向缓存，
 * 保证两向查询共享同一份事实；另有一张 {@code key→status} 状态缓存支撑 {@link #findStatusByKey(String)}（P3-T4 请求路径软拦读它判定
 * SUSPENDED，避免每请求回源）。当前阶段（Phase 3）租户数量 ≤ 3 位数且单节点部署，不做 TTL，
 * 读到的映射视作不变；状态变更（suspend/resume/provision）由写路径显式调用 {@link #invalidateByKey(String)} / {@link
 * #invalidateById(long)} / {@link #invalidateAll()} 失效，下次读取回源重建，避免陈旧映射（多节点失效广播留待 Phase 4+）。
 *
 * <p>本类实现 domain 端口 {@link TenantDirectory}（COLA 红线 #6/#7）：app 层生命周期用例与 adapter 层请求入口都只依赖该接口， 不直连本
 * infrastructure 具体类。
 *
 * <p>不存在或未激活的 key/id 直接抛 {@link IllegalStateException}——这是"配置/迁移/令牌"三方漂移的哨兵，宁可让请求 500 也不允许静默
 * fallback 到 {@code default} 而误读写其他租户的数据。仅在明确允许"未知即回退"的兼容路径（如旧 DB 行 tenant_id 缺失）才使用 {@link
 * #lookupKeyOrDefault}。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@Component
public class TenantIdResolver implements TenantDirectory {

    private static final Logger log = LoggerFactory.getLogger(TenantIdResolver.class);

    private final TenantMapper tenantMapper;

    private final ConcurrentMap<String, Long> keyToId = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> idToKey = new ConcurrentHashMap<>();

    /** key→status 缓存（P3-T4）：支撑 {@link #findStatusByKey(String)} 请求路径软拦，避免每请求回源 DB。 */
    private final ConcurrentMap<String, String> keyToStatus = new ConcurrentHashMap<>();

    public TenantIdResolver(@Lazy TenantMapper tenantMapper) {
        // @Lazy：TenantMapper→SqlSessionFactory→MybatisPlusAutoConfiguration 会回收集
        // mybatisPlusInterceptor
        // 而该 interceptor 又依赖本 resolver，构成构造期循环（saas.enabled=true 才触发，P1-T10 首次暴露）。
        // 延迟解析 mapper，本 bean 得以先行构造，首次 resolve()/lookupKey() 时再拿到真实 mapper。
        this.tenantMapper = tenantMapper;
    }

    private static String canonicalKey(TenantDO row) {
        return row.getTenantKey().trim().toLowerCase(Locale.ROOT);
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
        TenantDO row = selectByKey(normalizedKey);
        if (row == null) {
            throw new IllegalStateException("Unknown tenant_key: " + normalizedKey);
        }
        return row;
    }

    /** 按归一化 key 查库，未知返回 {@code null}（不抛）；供 {@link #loadByKey} 与 {@link #findStatusByKey} 共用。 */
    private TenantDO selectByKey(String normalizedKey) {
        return tenantMapper.selectOne(
                Wrappers.<TenantDO>lambdaQuery()
                        .eq(TenantDO::getTenantKey, normalizedKey)
                        .last("LIMIT 1"));
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
        keyToStatus.put(canonicalKey, row.getStatus());
        if (log.isDebugEnabled()) {
            log.debug("Resolved tenant_key={} ↔ tenant_id={}", canonicalKey, id);
        }
        return row;
    }

    /**
     * 查询租户当前状态（缓存加速；miss 回源 DB）。未知 / 空白 key 返回 {@link Optional#empty()}，<b>不抛</b>。
     *
     * <p>P3-T4 请求路径软拦读此判定 SUSPENDED：ACTIVE 命中缓存零回源；SUSPENDED 亦被缓存， 由 suspend/resume 写路径的 {@link
     * #invalidateByKey(String)} 失效后下次回源重建，实现"停用即时拒、恢复即时通"。
     */
    @Override
    public Optional<String> findStatusByKey(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            return Optional.empty();
        }
        String normalized = tenantKey.trim().toLowerCase(Locale.ROOT);
        String cached = keyToStatus.get(normalized);
        if (cached != null) {
            return Optional.of(cached);
        }
        TenantDO row = selectByKey(normalized);
        if (row == null) {
            return Optional.empty();
        }
        String status = row.getStatus();
        keyToStatus.put(canonicalKey(row), status);
        return Optional.ofNullable(status);
    }

    /** 显式失效某个 key 的全部缓存（键↔id 双向映射 + 状态）；P3-T4 生命周期写路径调用。 */
    @Override
    public void invalidateByKey(String tenantKey) {
        if (tenantKey == null) {
            return;
        }
        String normalized = tenantKey.trim().toLowerCase(Locale.ROOT);
        Long id = keyToId.remove(normalized);
        if (id != null) {
            idToKey.remove(id);
        }
        keyToStatus.remove(normalized);
    }

    /** 显式失效某个 id 的全部缓存（键↔id 双向映射 + 状态）；P3-T4 生命周期写路径调用。 */
    @Override
    public void invalidateById(long tenantId) {
        String key = idToKey.remove(tenantId);
        if (key != null) {
            keyToId.remove(key);
            keyToStatus.remove(key);
        }
    }

    /** 全量清空缓存（键↔id 双向映射 + 状态）；配置热更 / 测试隔离使用。 */
    @Override
    public void invalidateAll() {
        keyToId.clear();
        idToKey.clear();
        keyToStatus.clear();
    }
}
