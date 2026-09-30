package com.mouhin.knowledge.repository.infrastructure.milvus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.infrastructure.tenant.TenantIdResolver;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link MilvusVectorStoreService#storeChunks} 的 metadata 写入契约测试（P2-T2）。
 *
 * <p>覆盖 S-2 方案 b 落地范围：每条 chunk 都要在 Milvus JSON metadata 里带上 {@code tenant_id} 数值，供后续 {@code
 * IsEqualTo("tenant_id", ...)} 前置过滤消费（P2-T3 起接入检索侧）。
 *
 * <p>关键断言：
 *
 * <ul>
 *   <li>随行 {@code chunk.tenantKey} → 经 {@link TenantIdResolver#resolve(String)} 得数值 {@code
 *       tenant_id}，写入 metadata；
 *   <li>缺失 {@code tenantKey} 时回落到 {@link TenantContext#getOrDefault()}（未绑定即 {@code default}）；
 *   <li>{@code saas.enabled=false}（未 ThreadLocal 绑定）与 {@code true}（显式 {@code
 *       TenantContext.set("acme")}）行为等价：都写正确的 tenant_id；
 *   <li>解析失败（未知租户）由 {@link TenantIdResolver} 抛 {@code IllegalStateException}，向上传播（P1-T8 哨兵）；
 *   <li>空 chunks 列表短路返回，不触碰 resolver / store。
 * </ul>
 *
 * @author mouhinU
 * @date 2026-09-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MilvusVectorStoreService P2-T2 metadata.tenant_id 写入")
class MilvusVectorStoreServiceTest {

    @Mock private EmbeddingStoreProvider storeProvider;
    @Mock private EmbeddingStore<TextSegment> embeddingStore;
    @Mock private EmbeddingModel embeddingModel;
    @Mock private TenantIdResolver tenantIdResolver;

    private MilvusVectorStoreService service;

    @BeforeEach
    void setUp() {
        service = new MilvusVectorStoreService(storeProvider, embeddingModel, tenantIdResolver);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static DocumentChunk chunk(String documentKey, int index, String tenantKey) {
        DocumentChunk c = new DocumentChunk();
        c.setDocumentKey(documentKey);
        c.setChunkIndex(index);
        c.setStartPage(1);
        c.setEndPage(1);
        c.setContent("内容片段 " + index);
        c.setCategory("其他");
        c.setTenantKey(tenantKey);
        return c;
    }

    /** 打桩 embedAll：返回与 batch 等长的 dummy embedding 列表，并把 storeProvider.store() 串起来。 */
    private void stubEmbedAndStore(int totalChunks) {
        when(storeProvider.store()).thenReturn(embeddingStore);
        List<Embedding> embeddings = new java.util.ArrayList<>(totalChunks);
        for (int i = 0; i < totalChunks; i++) {
            embeddings.add(Embedding.from(new float[] {0f, 0f, 0f}));
        }
        when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(embeddings));
    }

    @Nested
    @DisplayName("随行 tenantKey 优先")
    class ChunkTenantKeyPreferred {

        @Test
        @DisplayName("chunk.tenantKey='acme' → resolver.resolve('acme') → metadata.tenant_id=2")
        void writesResolvedTenantId() {
            stubEmbedAndStore(1);
            when(tenantIdResolver.resolve("acme")).thenReturn(2L);

            service.storeChunks(List.of(chunk("doc-1", 0, "acme")));

            ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
            verify(embeddingStore, times(1)).addAll(anyList(), captor.capture());
            TextSegment segment = captor.getValue().get(0);
            assertThat(segment.metadata().getLong("tenant_id")).isEqualTo(2L);
        }

        @Test
        @DisplayName("ThreadLocal 已绑定 'beta' 但 chunk.tenantKey='acme' → 用 chunk 值 2L（随行优先）")
        void chunkTenantKeyBeatsThreadLocal() {
            stubEmbedAndStore(1);
            TenantContext.set("beta");
            when(tenantIdResolver.resolve("acme")).thenReturn(2L);

            service.storeChunks(List.of(chunk("doc-1", 0, "acme")));

            ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
            verify(embeddingStore).addAll(anyList(), captor.capture());
            assertThat(captor.getValue().get(0).metadata().getLong("tenant_id")).isEqualTo(2L);
            verify(tenantIdResolver, never()).resolve(eq("beta"));
        }
    }

    @Nested
    @DisplayName("TenantContext 回落")
    class ContextFallback {

        @Test
        @DisplayName("chunk.tenantKey=null + ThreadLocal 未绑定 → 走 default 回落并写 tenant_id=1")
        void defaultFallbackWhenNoContext() {
            stubEmbedAndStore(1);
            when(tenantIdResolver.resolve(TenantContext.DEFAULT_TENANT_KEY)).thenReturn(1L);

            service.storeChunks(List.of(chunk("doc-1", 0, null)));

            ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
            verify(embeddingStore).addAll(anyList(), captor.capture());
            assertThat(captor.getValue().get(0).metadata().getLong("tenant_id")).isEqualTo(1L);
            verify(tenantIdResolver).resolve(TenantContext.DEFAULT_TENANT_KEY);
        }

        @Test
        @DisplayName("chunk.tenantKey=空白 + ThreadLocal='acme' → 用 ThreadLocal 值")
        void blankChunkKeyFallsBackToThreadLocal() {
            stubEmbedAndStore(1);
            TenantContext.set("acme");
            when(tenantIdResolver.resolve("acme")).thenReturn(2L);

            service.storeChunks(List.of(chunk("doc-1", 0, "   ")));

            ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
            verify(embeddingStore).addAll(anyList(), captor.capture());
            assertThat(captor.getValue().get(0).metadata().getLong("tenant_id")).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("saas.enabled 行为等价（关闭态 vs 开启态都写 tenant_id）")
    class SaasFlagEquivalence {

        @Test
        @DisplayName("关闭态：ThreadLocal 未 set，走 default 且 metadata 依然带 tenant_id=1")
        void disabledStillWritesTenantId() {
            stubEmbedAndStore(1);
            when(tenantIdResolver.resolve(anyString())).thenReturn(1L);

            service.storeChunks(List.of(chunk("doc-1", 0, null)));

            ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
            verify(embeddingStore).addAll(anyList(), captor.capture());
            assertThat(captor.getValue().get(0).metadata().getLong("tenant_id")).isNotNull();
        }

        @Test
        @DisplayName("开启态：ThreadLocal 显式绑定 acme 且 chunk 未携带 tenantKey，走 ThreadLocal 值")
        void enabledUsesContextWhenChunkKeyMissing() {
            stubEmbedAndStore(1);
            TenantContext.set("acme");
            when(tenantIdResolver.resolve("acme")).thenReturn(2L);

            service.storeChunks(List.of(chunk("doc-1", 0, null)));

            ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
            verify(embeddingStore).addAll(anyList(), captor.capture());
            assertThat(captor.getValue().get(0).metadata().getLong("tenant_id")).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("多 chunk 批次")
    class MultiChunkBatch {

        @Test
        @DisplayName("同批 3 chunk 都携带 tenantKey='acme' → resolver 缓存路径下每 chunk 各调 1 次")
        void resolverCalledPerChunk() {
            stubEmbedAndStore(3);
            when(tenantIdResolver.resolve("acme")).thenReturn(2L);

            service.storeChunks(
                    List.of(
                            chunk("doc-1", 0, "acme"),
                            chunk("doc-1", 1, "acme"),
                            chunk("doc-1", 2, "acme")));

            ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
            verify(embeddingStore).addAll(anyList(), captor.capture());
            List<TextSegment> segments = captor.getValue();
            assertThat(segments).hasSize(3);
            assertThat(segments)
                    .allSatisfy(s -> assertThat(s.metadata().getLong("tenant_id")).isEqualTo(2L));
            verify(tenantIdResolver, times(3)).resolve("acme");
        }

        @Test
        @DisplayName("跨租户批次（当前不会出现，防御性覆盖）→ 各自写各自的 tenant_id")
        void perChunkTenantIsolation() {
            stubEmbedAndStore(2);
            when(tenantIdResolver.resolve("acme")).thenReturn(2L);
            when(tenantIdResolver.resolve("beta")).thenReturn(3L);

            service.storeChunks(List.of(chunk("doc-1", 0, "acme"), chunk("doc-1", 1, "beta")));

            ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
            verify(embeddingStore).addAll(anyList(), captor.capture());
            List<TextSegment> segments = captor.getValue();
            assertThat(segments.get(0).metadata().getLong("tenant_id")).isEqualTo(2L);
            assertThat(segments.get(1).metadata().getLong("tenant_id")).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("异常路径")
    class ErrorPaths {

        @Test
        @DisplayName("resolver 抛未知租户 → 向上传播 IllegalStateException（P1-T8 哨兵，不静默回退）")
        void resolverFailurePropagates() {
            when(tenantIdResolver.resolve(anyString()))
                    .thenThrow(new IllegalStateException("Unknown tenant_key: ghost"));

            assertThatThrownBy(() -> service.storeChunks(List.of(chunk("doc-1", 0, "ghost"))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Unknown tenant_key");

            verify(storeProvider, never()).store();
            verifyNoInteractions(embeddingStore);
        }

        @Test
        @DisplayName("空 chunks → 直接返回，不触碰 resolver / store / model")
        void emptyChunksShortCircuits() {
            service.storeChunks(List.of());
            verifyNoInteractions(tenantIdResolver, embeddingModel, storeProvider);
        }

        @Test
        @DisplayName("chunks=null → 同上短路")
        void nullChunksShortCircuits() {
            service.storeChunks(null);
            verifyNoInteractions(tenantIdResolver, embeddingModel, storeProvider);
        }
    }

    @Nested
    @DisplayName("metadata 其他键保持")
    class OtherMetadataPreserved {

        @Test
        @DisplayName("加入 tenant_id 不破坏原有 document_key / category 等 12 个键")
        void otherMetadataKeysIntact() {
            stubEmbedAndStore(1);
            when(tenantIdResolver.resolve("acme")).thenReturn(2L);

            DocumentChunk c = chunk("doc-1", 0, "acme");
            c.setDocumentName("手册.pdf");
            c.setFileType("pdf");
            c.setTags("a,b");
            c.setDepartmentId("d-1");
            c.setVisibility("INTERNAL");
            c.setAllowedRoles("[\"ROLE_X\"]");
            c.setOwnerId("user-1");

            service.storeChunks(List.of(c));

            ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
            verify(embeddingStore).addAll(anyList(), captor.capture());
            var m = captor.getValue().get(0).metadata();
            assertThat(m.getString("document_key")).isEqualTo("doc-1");
            assertThat(m.getString("document_name")).isEqualTo("手册.pdf");
            assertThat(m.getString("file_type")).isEqualTo("pdf");
            assertThat(m.getString("tags")).isEqualTo("a,b");
            assertThat(m.getString("chunk_index")).isEqualTo("0");
            assertThat(m.getString("start_page")).isEqualTo("1");
            assertThat(m.getString("end_page")).isEqualTo("1");
            assertThat(m.getString("department_id")).isEqualTo("d-1");
            assertThat(m.getString("visibility")).isEqualTo("INTERNAL");
            assertThat(m.getString("allowed_roles")).isEqualTo("[\"ROLE_X\"]");
            assertThat(m.getString("owner_id")).isEqualTo("user-1");
            assertThat(m.getString("category")).isEqualTo("其他");
            assertThat(m.getLong("tenant_id")).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("P2-T3 检索侧 tenant_id 前置过滤下推")
    class SearchFilterPushdown {

        @Test
        @DisplayName("无 category → filter 即 IsEqualTo(tenant_id, 2)")
        void noCategoryOnlyTenantFilter() {
            stubEmbedAndEmptySearch();
            TenantContext.set("acme");
            when(tenantIdResolver.resolve("acme")).thenReturn(2L);

            service.search("hello", 5, 0.5, null, null);

            dev.langchain4j.store.embedding.filter.Filter filter = captureRequest().filter();
            assertThat(filter).isInstanceOf(IsEqualTo.class);
            IsEqualTo eq = (IsEqualTo) filter;
            assertThat(eq.key()).isEqualTo("tenant_id");
            assertThat(eq.comparisonValue()).isEqualTo(2L);
        }

        @Test
        @DisplayName("有 category → filter 为 And(IsEqualTo(tenant_id), IsEqualTo(category))")
        void withCategoryAndComposed() {
            stubEmbedAndEmptySearch();
            when(tenantIdResolver.resolve(TenantContext.DEFAULT_TENANT_KEY)).thenReturn(1L);

            service.search("hello", 5, 0.5, null, "手册");

            var filter = captureRequest().filter();
            assertThat(filter).isInstanceOf(And.class);
            And and = (And) filter;
            assertThat(((IsEqualTo) and.left()).key()).isEqualTo("tenant_id");
            assertThat(((IsEqualTo) and.left()).comparisonValue()).isEqualTo(1L);
            assertThat(((IsEqualTo) and.right()).key()).isEqualTo("category");
            assertThat(((IsEqualTo) and.right()).comparisonValue()).isEqualTo("手册");
        }

        @Test
        @DisplayName("空白 category 视为无过滤 → 只保留 tenant_id")
        void blankCategoryIgnored() {
            stubEmbedAndEmptySearch();
            when(tenantIdResolver.resolve(TenantContext.DEFAULT_TENANT_KEY)).thenReturn(1L);

            service.search("hello", 5, 0.5, null, "   ");

            dev.langchain4j.store.embedding.filter.Filter filter = captureRequest().filter();
            assertThat(filter).isInstanceOf(IsEqualTo.class);
            assertThat(((IsEqualTo) filter).key()).isEqualTo("tenant_id");
        }

        @Test
        @DisplayName("ThreadLocal 未绑定 → 走 default 回落并带 tenant_id=1")
        void unboundContextFallsBackToDefault() {
            stubEmbedAndEmptySearch();
            TenantContext.clear();
            when(tenantIdResolver.resolve(TenantContext.DEFAULT_TENANT_KEY)).thenReturn(1L);

            service.search("hello", 5, 0.5, null);

            var filter = captureRequest().filter();
            assertThat(filter).isInstanceOf(IsEqualTo.class);
            assertThat(((IsEqualTo) filter).comparisonValue()).isEqualTo(1L);
        }

        @Test
        @DisplayName("filterExpr (ACL 字符串) 参数被忽略但签名兼容,仍带 tenant_id")
        void legacyFilterExprArgumentIgnored() {
            stubEmbedAndEmptySearch();
            when(tenantIdResolver.resolve(TenantContext.DEFAULT_TENANT_KEY)).thenReturn(1L);

            service.search("hello", 5, 0.5, "visibility == \"PUBLIC\"");

            var filter = captureRequest().filter();
            assertThat(filter).isInstanceOf(IsEqualTo.class);
            assertThat(((IsEqualTo) filter).key()).isEqualTo("tenant_id");
        }

        @Test
        @DisplayName("resolver 抛未知租户 → search 立即失败（P1-T8 哨兵），不触达 store")
        void resolverFailurePropagatesInSearch() {
            when(embeddingModel.embed("hello"))
                    .thenReturn(Response.from(Embedding.from(new float[] {0f})));
            when(tenantIdResolver.resolve(anyString()))
                    .thenThrow(new IllegalStateException("Unknown tenant_key: ghost"));
            TenantContext.set("ghost");

            assertThatThrownBy(() -> service.search("hello", 5, 0.5, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Unknown tenant_key");

            verify(storeProvider, never()).store();
        }

        @Test
        @DisplayName("跨租户 A 建 B 查：A 上下文下 resolver 得 2L,filter 谓词值是 2L(B 上下文的 3L 数据被 JSON 谓词排除)")
        void crossTenantQueryCarriesCallerTenantOnly() {
            stubEmbedAndEmptySearch();
            TenantContext.set("acme");
            when(tenantIdResolver.resolve("acme")).thenReturn(2L);

            service.search("hello", 5, 0.5, null);

            var filter = captureRequest().filter();
            assertThat(((IsEqualTo) filter).comparisonValue()).isEqualTo(2L);
            // 断言：不存在"跨租户" 3L——spike §2 已证 JSON 谓词层 0 泄漏，此处验证谓词值只带当前上下文
            assertThat(((IsEqualTo) filter).comparisonValue()).isNotEqualTo(3L);
        }

        private void stubEmbedAndEmptySearch() {
            when(embeddingModel.embed(anyString()))
                    .thenReturn(Response.from(Embedding.from(new float[] {0f, 0f, 0f})));
            when(storeProvider.store()).thenReturn(embeddingStore);
            @SuppressWarnings("unchecked")
            EmbeddingSearchResult<TextSegment> emptyResult = mock(EmbeddingSearchResult.class);
            when(emptyResult.matches()).thenReturn(List.of());
            when(embeddingStore.search(any())).thenReturn(emptyResult);
        }

        private EmbeddingSearchRequest captureRequest() {
            ArgumentCaptor<EmbeddingSearchRequest> captor =
                    ArgumentCaptor.forClass(EmbeddingSearchRequest.class);
            verify(embeddingStore).search(captor.capture());
            return captor.getValue();
        }
    }
}
