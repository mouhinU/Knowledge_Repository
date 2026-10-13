package com.mouhin.knowledge.repository.infrastructure.milvus;

import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantPlacementDO;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Milvus EmbeddingStore 路由持有器（P2-T1，Phase 2 方案 b 骨架）。
 *
 * <p>SaaS Phase 2 采纳 <b>方案 b</b>：现网 {@code knowledge_chunks} 集合保持 LangChain4j 4 列固定 schema（id /
 * text / metadata(JSON) / vector），租户隔离走 {@code metadata.tenant_id} 的 {@link
 * dev.langchain4j.store.embedding.filter.comparison.IsEqualTo} JSON 谓词下推（spike 已证 0
 * leak）。partition-key + SILO 独立集合为 <b>Phase 3 目标态</b>，本类作为骨架先把"按租户取 store"的接缝拉出来，Phase 3 只需替换
 * {@link EmbeddingStoreRouter} 内部实现即可，业务侧无感。
 *
 * <p><b>Phase 2 语义（等价于关闭态）</b>：内部只代理 {@link MilvusConfig#embeddingStore()} 提供的 {@code @Lazy}
 * 单例；无论传入的 {@code tenantKey} / {@code placement} 为何，{@link #store()} 与 {@link #storeFor(String,
 * String)} 恒返回同一实例。这保证 {@code knowledge.saas.enabled=false} 与 {@code true} 运行行为完全等价，符合提案 §9 回退闸门。
 *
 * <p><b>缓存与热切换（T-1 消解）</b>：方案 b 无多 store 分片，本类不做本地缓存与 TTL；Phase 3 引入 SILO 时会在此追加 {@code
 * ConcurrentMap<tenantKey, EmbeddingStore>} + 显式失效钩子（对齐 Redis pub/sub，见 P5-T4 副本化条目）。
 *
 * <p><b>监控基数（C-2 自检）</b>：本类<b>不</b>向 {@code MeterRegistry} 注册任何带 {@code tenant} tag 的指标； 租户级用量走
 * {@code tenant_usage} 落库（P6-T1），不进 Prometheus 时间序列。
 *
 * @author mouhinU
 * @date 2026-09-30
 */
@Component
@Slf4j
public class EmbeddingStoreProvider {

    /** Phase 2 唯一 store（LangChain4j 4 列 schema + {@code knowledge_chunks} 集合）。 */
    private final EmbeddingStore<TextSegment> poolStore;

    /** 路由策略；Phase 2 恒返回 {@link #poolStore}，Phase 3 换入 SILO 分片实现。 */
    private final EmbeddingStoreRouter router;

    public EmbeddingStoreProvider(
            @Lazy EmbeddingStore<TextSegment> poolStore, EmbeddingStoreRouter router) {
        this.poolStore = Objects.requireNonNull(poolStore, "poolStore");
        this.router = Objects.requireNonNull(router, "router");
        if (log.isDebugEnabled()) {
            log.debug("EmbeddingStoreProvider initialised (Phase 2 single-store mode)");
        }
    }

    /**
     * 主流程默认 store：等价于 {@code storeFor(TenantContext.DEFAULT_TENANT_KEY, PLACEMENT_POOL)}。 Phase 2
     * 现网仅有一租户一集合，业务侧调用点先走本方法拿实例；跨租户负向验证与 SILO 生命周期改动到 {@link #storeFor} 上。
     *
     * @return POOL 集合的 {@link EmbeddingStore}
     */
    public EmbeddingStore<TextSegment> store() {
        return poolStore;
    }

    /**
     * 按租户与放置策略路由 store。Phase 2 恒返回 {@link #store()}；Phase 3 SILO 落地后 {@link EmbeddingStoreRouter}
     * 会针对 {@link TenantPlacementDO#PLACEMENT_SILO} 返回独立集合实例。
     *
     * @param tenantKey 租户业务键，允许 {@code null}（视为默认租户）
     * @param placement 放置策略：{@code POOL} / {@code SILO}；{@code null} 或未知值视为 {@code POOL}
     * @return 对应路由的 {@link EmbeddingStore}
     */
    public EmbeddingStore<TextSegment> storeFor(String tenantKey, String placement) {
        return router.route(tenantKey, placement, poolStore);
    }
}
