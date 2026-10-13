package com.mouhin.knowledge.repository.infrastructure.milvus;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantPlacementDO;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

/**
 * EmbeddingStore 路由策略（P2-T1 骨架）。
 *
 * <p>Phase 2 采纳方案 b：LangChain4j 单 store + {@code metadata.tenant_id} JSON 谓词路径，本类**恒**返回 调用方传入的
 * {@code poolStore}，SILO 分支保留但运行时不放开（对齐 S-1 方案 B 授权延后至 Phase 3）。
 *
 * <p>Phase 3 目标态：本类内部会持有 {@code ConcurrentMap<tenantKey, EmbeddingStore>}（SILO 独立集合的实例表）， 并按 {@code
 * placement} 参数二选一：{@link TenantPlacementDO#PLACEMENT_SILO} → 独立集合 store； {@link
 * TenantPlacementDO#PLACEMENT_POOL} → 共享 {@code poolStore}。同时引入 {@code invalidate(tenantKey)} 供
 * {@code TenantLifecycleService} 在租户迁移 / 归档时清理陈旧实例。
 *
 * <p>路由函数无副作用（相同输入 → 相同输出），符合红线 #12 优先纯函数原则；实例表与失效在 Phase 3 追加时同步保持"读缓存 miss 才建连" 的懒加载形态。
 *
 * @author mouhinU
 * @date 2026-09-30
 */
@Component
@Slf4j
public class EmbeddingStoreRouter {

    private static String normalisePlacement(String placement) {
        if (placement == null || placement.isBlank()) {
            return TenantPlacementDO.PLACEMENT_POOL;
        }
        String upper = placement.trim().toUpperCase(Locale.ROOT);
        if (TenantPlacementDO.PLACEMENT_POOL.equals(upper)
                || TenantPlacementDO.PLACEMENT_SILO.equals(upper)) {
            return upper;
        }
        // 未知值一律回退 POOL：Phase 2 SILO 未落地，宁可误降级也不允许 NPE / 未定义分支
        return TenantPlacementDO.PLACEMENT_POOL;
    }

    /**
     * 按租户与放置策略选择目标 store。
     *
     * <p>Phase 2 无论 {@code placement} 传入何值（含 {@code SILO}），均返回 {@code poolStore}；这是刻意的 "接缝先行"，Phase
     * 3 打开 SILO 时只替换本方法内部实现，调用点与 {@link EmbeddingStoreProvider} 均无需改动。
     *
     * @param tenantKey 租户业务键；当前 Phase 2 不参与决策，仅记录 debug 日志便于后续迁移；{@code null} 视为 {@link
     *                  TenantContext#DEFAULT_TENANT_KEY}
     * @param placement 放置策略字面量（{@code POOL} / {@code SILO}）；未知或 {@code null} 归一为 {@code POOL}
     * @param poolStore 共享集合（POOL）实例，由 {@link EmbeddingStoreProvider} 传入
     * @return Phase 2 恒为 {@code poolStore}
     */
    public EmbeddingStore<TextSegment> route(
            String tenantKey, String placement, EmbeddingStore<TextSegment> poolStore) {
        Objects.requireNonNull(poolStore, "poolStore must not be null");
        String effectiveKey =
                (tenantKey == null || tenantKey.isBlank())
                        ? TenantContext.DEFAULT_TENANT_KEY
                        : tenantKey.trim().toLowerCase(Locale.ROOT);
        String effectivePlacement = normalisePlacement(placement);
        if (log.isDebugEnabled()) {
            log.debug(
                    "route(tenantKey={}, placement={}) → POOL store (Phase 2 single-store mode)",
                    effectiveKey,
                    effectivePlacement);
        }
        // Phase 3 SILO 分支占位：if (PLACEMENT_SILO.equalsIgnoreCase(effectivePlacement)) {
        //     return siloStoreRegistry.computeIfAbsent(effectiveKey, this::createSiloStore);
        // }
        return poolStore;
    }
}
