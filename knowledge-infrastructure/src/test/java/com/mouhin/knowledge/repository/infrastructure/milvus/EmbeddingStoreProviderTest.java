package com.mouhin.knowledge.repository.infrastructure.milvus;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

/**
 * {@link EmbeddingStoreProvider} 单元测试（P2-T1）。
 *
 * <p>覆盖 Phase 2 方案 b 契约：
 *
 * <ul>
 *   <li>非 null 单例：{@link EmbeddingStoreProvider#store()} 恒返回注入的 {@code poolStore}；
 *   <li>路由透传：{@link EmbeddingStoreProvider#storeFor(String, String)} 委派 {@link
 *       EmbeddingStoreRouter}；
 *   <li>构造期校验：{@code null} pool store / {@code null} router 直接抛 NPE，避免运行期才暴露装配缺失；
 *   <li>关闭态等价：{@code saas.enabled=false}（未走路由分支）与 {@code true}（走 {@code storeFor}）返回同一实例。
 * </ul>
 *
 * @author mouhinU
 * @date 2026-09-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingStoreProvider Phase 2 单 store 契约")
class EmbeddingStoreProviderTest {

    @Mock
    private EmbeddingStore<TextSegment> poolStore;
    @Mock
    private EmbeddingStoreRouter router;

    private EmbeddingStoreProvider provider;

    @BeforeEach
    void setUp() {
        provider = new EmbeddingStoreProvider(poolStore, router);
    }

    @Nested
    @DisplayName("store() 默认路径")
    class DefaultStore {

        @Test
        @DisplayName("返回注入的 poolStore 实例（同引用）")
        void store_returnsInjectedSingleton() {
            assertThat(provider.store()).isSameAs(poolStore);
        }

        @Test
        @DisplayName("不触碰 router（Phase 2 单例走快路径）")
        void store_doesNotConsultRouter() {
            provider.store();
            verifyNoInteractions(router);
        }
    }

    @Nested
    @DisplayName("storeFor() 路由路径")
    class RoutedStore {

        @Test
        @DisplayName("委派 router.route(tenantKey, placement, poolStore)")
        void storeFor_delegatesToRouter() {
            when(router.route(eq("acme"), eq(TenantPlacementDO.PLACEMENT_POOL), same(poolStore)))
                    .thenReturn(poolStore);

            EmbeddingStore<TextSegment> result =
                    provider.storeFor("acme", TenantPlacementDO.PLACEMENT_POOL);

            assertThat(result).isSameAs(poolStore);
            verify(router).route("acme", TenantPlacementDO.PLACEMENT_POOL, poolStore);
        }

        @Test
        @DisplayName("Phase 2 SILO 分支同样透传到 router（router 决定实际是否走独立集合）")
        void storeFor_siloPlacementAlsoRouted() {
            when(router.route(eq("acme"), eq(TenantPlacementDO.PLACEMENT_SILO), same(poolStore)))
                    .thenReturn(poolStore);

            EmbeddingStore<TextSegment> result =
                    provider.storeFor("acme", TenantPlacementDO.PLACEMENT_SILO);

            assertThat(result).isSameAs(poolStore);
            verify(router).route("acme", TenantPlacementDO.PLACEMENT_SILO, poolStore);
        }

        @Test
        @DisplayName("saas.enabled=true + 空参 → 走 router（tenantKey=null 由 router 归一为 default）")
        void storeFor_nullTenantKeyStillRouted() {
            when(router.route(null, null, poolStore)).thenReturn(poolStore);

            EmbeddingStore<TextSegment> result = provider.storeFor(null, null);

            assertThat(result).isSameAs(poolStore);
            verify(router).route(null, null, poolStore);
        }
    }

    @Nested
    @DisplayName("构造期 null 校验")
    class ConstructorGuards {

        @Test
        @DisplayName("poolStore 为 null 抛 NPE")
        void nullPoolStoreRejected() {
            assertThatThrownBy(() -> new EmbeddingStoreProvider(null, router))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("poolStore");
        }

        @Test
        @DisplayName("router 为 null 抛 NPE")
        void nullRouterRejected() {
            assertThatThrownBy(() -> new EmbeddingStoreProvider(poolStore, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("router");
        }
    }

    @Nested
    @DisplayName("TenantContext 兼容性")
    class TenantContextInterop {

        @Test
        @DisplayName("未绑定 ThreadLocal 时 store() 不感知（Phase 2 单例语义）")
        void storeIgnoresTenantContext() {
            TenantContext.clear();
            assertThat(provider.store()).isSameAs(poolStore);
            verifyNoInteractions(router);
        }
    }
}
