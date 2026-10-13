package com.mouhin.knowledge.repository.infrastructure.milvus;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.domain.service.IndexProgressCallback;
import com.mouhin.knowledge.repository.infrastructure.tenant.TenantIdResolver;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Milvus 向量存储服务
 *
 * <p>封装 LangChain4j EmbeddingStore 操作，提供文档向量写入和带权限过滤的语义检索。
 *
 * <p>P2-T1 起：store 实例经 {@link EmbeddingStoreProvider} 路由获取，Phase 2 方案 b 下恒返回 {@code
 * knowledge_chunks} 单例；Phase 3 SILO 打开后同一接口会按 {@code placement} 分派到独立集合，调用点无需改动。
 *
 * @author mouhinU
 * @date 2026-09-02
 */
@Service
@Slf4j
public class MilvusVectorStoreService implements VectorStoreGateway {

    /** 每批向量化处理的分块数量 */
    private static final int EMBEDDING_BATCH_SIZE = 20;

    private final EmbeddingStoreProvider storeProvider;
    private final EmbeddingModel embeddingModel;
    private final TenantIdResolver tenantIdResolver;

    public MilvusVectorStoreService(
            EmbeddingStoreProvider storeProvider,
            EmbeddingModel embeddingModel,
            TenantIdResolver tenantIdResolver) {
        this.storeProvider = storeProvider;
        this.embeddingModel = embeddingModel;
        this.tenantIdResolver = tenantIdResolver;
    }

    /**
     * 解析当前 chunk 应归属的 tenant_key：优先随行字段（P2-T2 app / domain 构造点已注入），缺失则回落到 {@link
     * TenantContext#getOrDefault()}。
     *
     * <p>回落覆盖两类场景：(1) 老数据重灌入口未走 P2-T2 之后的构造点；(2) 单元测试直接 {@code new DocumentChunk()} 未设
     * tenantKey。任一路径最终仍由 {@link TenantIdResolver#resolve(String)} 校验租户存在性，未知即抛 {@code
     * IllegalStateException}（P1-T8 哨兵）。
     */
    private static String effectiveTenantKey(DocumentChunk chunk) {
        String key = chunk.getTenantKey();
        return (key == null || key.isBlank()) ? TenantContext.getOrDefault() : key;
    }

    /**
     * 批量向量化并存储文档分块
     *
     * @param chunks 文档分块列表
     */
    @Override
    public void storeChunks(List<DocumentChunk> chunks) {
        storeChunks(chunks, null);
    }

    /**
     * 批量向量化并存储文档分块，带进度回调
     *
     * @param chunks 文档分块列表
     * @param callback 进度回调（可为 null）
     */
    @Override
    public void storeChunks(List<DocumentChunk> chunks, IndexProgressCallback callback) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        int totalChunks = chunks.size();

        // 构建所有 TextSegment
        List<TextSegment> segments = new ArrayList<>(totalChunks);
        for (DocumentChunk chunk : chunks) {
            // P2-T2：随行租户标识→数值 tenant_id，写入 Milvus metadata JSON（S-2 方案 b：JSON 谓词路径）
            long tenantId = tenantIdResolver.resolve(effectiveTenantKey(chunk));
            Metadata metadata =
                    new Metadata()
                            .put("tenant_id", tenantId)
                            .put("document_key", chunk.getDocumentKey())
                            .put(
                                    "document_name",
                                    chunk.getDocumentName() != null ? chunk.getDocumentName() : "")
                            .put(
                                    "file_type",
                                    chunk.getFileType() != null ? chunk.getFileType() : "")
                            .put("tags", chunk.getTags() != null ? chunk.getTags() : "")
                            .put("chunk_index", String.valueOf(chunk.getChunkIndex()))
                            .put("start_page", String.valueOf(chunk.getStartPage()))
                            .put("end_page", String.valueOf(chunk.getEndPage()))
                            .put(
                                    "department_id",
                                    chunk.getDepartmentId() != null ? chunk.getDepartmentId() : "")
                            .put(
                                    "visibility",
                                    chunk.getVisibility() != null
                                            ? chunk.getVisibility()
                                            : "INTERNAL")
                            .put(
                                    "allowed_roles",
                                    chunk.getAllowedRoles() != null ? chunk.getAllowedRoles() : "")
                            .put("owner_id", chunk.getOwnerId() != null ? chunk.getOwnerId() : "")
                            .put(
                                    "category",
                                    chunk.getCategory() != null ? chunk.getCategory() : "其他");

            TextSegment segment = TextSegment.from(chunk.getContent(), metadata);
            segments.add(segment);
        }

        // 分批向量化 + 存储
        List<Embedding> allEmbeddings = new ArrayList<>(totalChunks);
        for (int i = 0; i < totalChunks; i += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(i + EMBEDDING_BATCH_SIZE, totalChunks);
            List<TextSegment> batch = segments.subList(i, end);

            List<Embedding> batchEmbeddings = embeddingModel.embedAll(batch).content();
            allEmbeddings.addAll(batchEmbeddings);

            if (callback != null) {
                callback.onProgress(end, totalChunks);
            }
        }

        // 一次性存储到 Milvus
        storeProvider.store().addAll(allEmbeddings, segments);

        log.info("Stored {} chunks in Milvus", totalChunks);
    }

    /**
     * 语义检索，支持权限过滤和分类过滤
     *
     * @param query 查询文本
     * @param maxResults 最大返回数量
     * @param minScore 最低相似度阈值
     * @param filterExpr Milvus 过滤表达式（权限），null 表示不过滤
     * @param category 文档分类过滤，null 或空表示不过滤
     * @return 检索结果列表
     */
    @Override
    public List<SearchResult> search(
            String query, int maxResults, double minScore, String filterExpr, String category) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        var requestBuilder =
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(maxResults)
                        .minScore(minScore);

        // P2-T3：tenant_id 前置谓词强制下推（S-2 方案 b）；ACL filterExpr 由应用层后过滤，本方法参数保留兼容签名。
        long currentTenantId = tenantIdResolver.resolve(TenantContext.getOrDefault());
        Filter filter = buildFilter(currentTenantId, category);
        requestBuilder.filter(filter);

        EmbeddingSearchResult<TextSegment> result =
                storeProvider.store().search(requestBuilder.build());

        List<SearchResult> searchResults = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            TextSegment segment = match.embedded();
            Metadata metadata = segment.metadata();

            searchResults.add(
                    new SearchResult(
                            segment.text(),
                            metadata.getString("document_key"),
                            metadata.getString("document_name"),
                            parseIntOrNull(metadata.getString("start_page")),
                            parseIntOrNull(metadata.getString("chunk_index")),
                            match.score(),
                            metadata.getString("category")));
        }

        log.debug("Search for '{}' returned {} results", query, searchResults.size());
        return searchResults;
    }

    /** 语义检索（无分类过滤，向后兼容） */
    @Override
    public List<SearchResult> search(
            String query, int maxResults, double minScore, String filterExpr) {
        return search(query, maxResults, minScore, filterExpr, null);
    }

    /**
     * 构建 LangChain4j Filter：租户前置过滤（必带）+ 可选分类过滤。
     *
     * <p>P2-T3 起：{@code tenant_id} 作为**强制**前置谓词下推到 Milvus JSON 层（S-2 方案 b： {@code
     * metadata["tenant_id"]==X}）。spike（{@code spikes/milvus-partition-key/RESULTS.md} §2） 已证与
     * partition-key 谓词 结果集完全一致、0 泄漏；Phase 3 partition-key 引入时 本行改为对顶层 Int64 列过滤并享受分区剪枝收益。
     *
     * <p>分类过滤：走 LangChain4j {@link IsEqualTo}，通过 {@link Filter#and(Filter)} 与租户谓词 AND 组合。
     *
     * <p>ACL 过滤（{@code filterExpr}）：仍由应用层 {@code AuthorizedSearchSupport.filterAccessible} 后过滤。
     * 原因：<b>不是</b> LangChain4j 不支持复杂表达式（{@code MilvusMetadataFilterMapper} 可译 {@code
     * And/Or/Not/In/ContainsString} 及基础比较），而是 {@code PermissionDomainService.buildFilterExpression}
     * 当前生成的是 **Milvus 原生字符串** 表达式，缺一个反向解析器把字符串读回 Filter 对象。 后过滤保留作双保险；SEC-1 修复仍生效。反向解析器留待后续独立 PR。
     *
     * <p>注意：本方法**总是返回非 null**（tenant_id 谓词强制）。旧的"category 空则不过滤"分支已在 P2-T3 移除。
     *
     * @param currentTenantId 已由 {@link TenantIdResolver#resolve(String)} 解析出的数值租户 id
     * @param category 文档分类过滤字面量；{@code null} 或空白视为不带
     * @return 至少含 {@code IsEqualTo("tenant_id", currentTenantId)} 的 {@link Filter}
     */
    private Filter buildFilter(long currentTenantId, String category) {
        Filter tenantFilter = new IsEqualTo("tenant_id", currentTenantId);
        if (category == null || category.isBlank()) {
            return tenantFilter;
        }
        return Filter.and(tenantFilter, new IsEqualTo("category", category));
    }

    /**
     * 删除指定文档的所有向量
     *
     * @param documentKey 文档唯一标识
     */
    @Override
    public void deleteByDocumentKey(String documentKey) {
        Filter filter = new IsEqualTo("document_key", documentKey);
        storeProvider.store().removeAll(filter);
        log.info("Deleted vectors for document {}", documentKey);
    }

    /**
     * 删除指定租户的所有向量（P3-T5 租户硬删）。
     *
     * <p>按 metadata 数值 {@code tenant_id} 等值谓词 {@code removeAll}，与写入侧（{@link #storeChunks}）及检索侧
     * （{@link #buildFilter}）的 S-2 方案 b JSON 谓词路径完全对齐。幂等：租户无向量时为空操作。
     *
     * @param tenantId 数值租户主键
     */
    @Override
    public void deleteByTenantId(long tenantId) {
        Filter filter = new IsEqualTo("tenant_id", tenantId);
        storeProvider.store().removeAll(filter);
        log.info("Deleted all vectors for tenant {}", tenantId);
    }

    private Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
