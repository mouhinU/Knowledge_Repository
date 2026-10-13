package com.mouhin.knowledge.repository.infrastructure.milvus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantPlacementDO;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link EmbeddingStoreRouter} 单元测试（P2-T1）。
 *
 * <p>Phase 2 契约：无论 {@code tenantKey} / {@code placement} 传什么， {@link
 * EmbeddingStoreRouter#route(String, String, EmbeddingStore)} 恒返回入参 {@code poolStore}。
 * 覆盖：POOL/SILO/未知/大小写/空白/null 六种 placement 归一；空/null tenantKey 回落到默认； null poolStore 直接 NPE（不允许静默
 * fallback）。
 *
 * @author mouhinU
 * @date 2026-09-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingStoreRouter Phase 2 恒单 store")
class EmbeddingStoreRouterTest {

    @Mock private EmbeddingStore<TextSegment> poolStore;

    private EmbeddingStoreRouter router;

    @BeforeEach
    void setUp() {
        router = new EmbeddingStoreRouter();
    }

    @Nested
    @DisplayName("Phase 2 恒返回 poolStore")
    class AlwaysPool {

        @Test
        @DisplayName("POOL 显式指定 → poolStore")
        void poolReturnsPoolStore() {
            assertThat(router.route("default", TenantPlacementDO.PLACEMENT_POOL, poolStore))
                    .isSameAs(poolStore);
        }

        @Test
        @DisplayName("SILO 也走 poolStore（Phase 3 前不放开，S-1 方案 B 授权延后）")
        void siloFallsBackToPoolStore() {
            assertThat(router.route("acme", TenantPlacementDO.PLACEMENT_SILO, poolStore))
                    .isSameAs(poolStore);
        }

        @Test
        @DisplayName("未知 placement 归一 POOL")
        void unknownPlacementNormalisesToPool() {
            assertThat(router.route("acme", "HYBRID", poolStore)).isSameAs(poolStore);
        }

        @Test
        @DisplayName("null placement 归一 POOL")
        void nullPlacementNormalisesToPool() {
            assertThat(router.route("acme", null, poolStore)).isSameAs(poolStore);
        }

        @Test
        @DisplayName("空白 placement 归一 POOL")
        void blankPlacementNormalisesToPool() {
            assertThat(router.route("acme", "   ", poolStore)).isSameAs(poolStore);
        }

        @Test
        @DisplayName("大小写混合 placement 归一（小写 pool / 混合 Silo 均视为合法）")
        void mixedCasePlacementAccepted() {
            assertThat(router.route("acme", "pool", poolStore)).isSameAs(poolStore);
            assertThat(router.route("acme", "Silo", poolStore)).isSameAs(poolStore);
            assertThat(router.route("acme", "SILO", poolStore)).isSameAs(poolStore);
        }

        @Test
        @DisplayName("placement 前后空白被 trim 后仍视为 POOL")
        void placementTrimmed() {
            assertThat(router.route("acme", "  POOL  ", poolStore)).isSameAs(poolStore);
        }
    }

    @Nested
    @DisplayName("tenantKey 归一")
    class TenantKeyNormalisation {

        @Test
        @DisplayName("null tenantKey → 走 default 归一，仍返回 poolStore")
        void nullTenantKeyDefaults() {
            assertThat(router.route(null, TenantPlacementDO.PLACEMENT_POOL, poolStore))
                    .isSameAs(poolStore);
        }

        @Test
        @DisplayName("空白 tenantKey → 走 default 归一")
        void blankTenantKeyDefaults() {
            assertThat(router.route("   ", TenantPlacementDO.PLACEMENT_POOL, poolStore))
                    .isSameAs(poolStore);
        }

        @Test
        @DisplayName("tenantKey 与 {@link TenantContext#DEFAULT_TENANT_KEY} 一致时正常路由")
        void explicitDefaultTenantKey() {
            assertThat(
                            router.route(
                                    TenantContext.DEFAULT_TENANT_KEY,
                                    TenantPlacementDO.PLACEMENT_POOL,
                                    poolStore))
                    .isSameAs(poolStore);
        }

        @Test
        @DisplayName("tenantKey 大小写混合（ACME vs acme）不影响路由结果")
        void tenantKeyCaseInsensitive() {
            assertThat(router.route("ACME", TenantPlacementDO.PLACEMENT_POOL, poolStore))
                    .isSameAs(poolStore);
            assertThat(router.route("acme", TenantPlacementDO.PLACEMENT_POOL, poolStore))
                    .isSameAs(poolStore);
        }
    }

    @Nested
    @DisplayName("构造期校验")
    class GuardClauses {

        @Test
        @DisplayName("poolStore 为 null 抛 NPE（Phase 2 唯一实例，缺失即配置错误）")
        void nullPoolStoreRejected() {
            assertThatThrownBy(() -> router.route("default", "POOL", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("poolStore");
        }
    }

    @Nested
    @DisplayName("纯函数与稳定性")
    class PurityAndStability {

        @Test
        @DisplayName("同参数多次调用返回同一引用（Phase 2 无状态）")
        void repeatedCallsStable() {
            EmbeddingStore<TextSegment> first =
                    router.route("acme", TenantPlacementDO.PLACEMENT_POOL, poolStore);
            EmbeddingStore<TextSegment> second =
                    router.route("acme", TenantPlacementDO.PLACEMENT_POOL, poolStore);
            assertThat(first).isSameAs(second).isSameAs(poolStore);
        }
    }
}
