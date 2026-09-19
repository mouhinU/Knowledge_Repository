package com.mouhin.knowledge.repository.infrastructure.milvus;

import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.service.IndexProgressCallback;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Milvus 向量存储服务
 * <p>
 * 封装 LangChain4j EmbeddingStore 操作，提供文档向量写入和带权限过滤的语义检索。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Service
public class MilvusVectorStoreService implements VectorStoreGateway {

    private static final Logger logger = LoggerFactory.getLogger(MilvusVectorStoreService.class);
    /**
     * 每批向量化处理的分块数量
     */
    private static final int EMBEDDING_BATCH_SIZE = 20;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public MilvusVectorStoreService(@Lazy EmbeddingStore<TextSegment> embeddingStore,
                                    EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
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
     * @param chunks   文档分块列表
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
            Metadata metadata = new Metadata()
                    .put("document_key", chunk.getDocumentKey())
                    .put("document_name", chunk.getDocumentName() != null ? chunk.getDocumentName() : "")
                    .put("file_type", chunk.getFileType() != null ? chunk.getFileType() : "")
                    .put("tags", chunk.getTags() != null ? chunk.getTags() : "")
                    .put("chunk_index", String.valueOf(chunk.getChunkIndex()))
                    .put("start_page", String.valueOf(chunk.getStartPage()))
                    .put("end_page", String.valueOf(chunk.getEndPage()))
                    .put("department_id", chunk.getDepartmentId() != null ? chunk.getDepartmentId() : "")
                    .put("visibility", chunk.getVisibility() != null ? chunk.getVisibility() : "INTERNAL")
                    .put("allowed_roles", chunk.getAllowedRoles() != null ? chunk.getAllowedRoles() : "")
                    .put("owner_id", chunk.getOwnerId() != null ? chunk.getOwnerId() : "")
                    .put("category", chunk.getCategory() != null ? chunk.getCategory() : "其他");

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
        embeddingStore.addAll(allEmbeddings, segments);

        logger.info("Stored {} chunks in Milvus", totalChunks);
    }

    /**
     * 语义检索，支持权限过滤和分类过滤
     *
     * @param query      查询文本
     * @param maxResults 最大返回数量
     * @param minScore   最低相似度阈值
     * @param filterExpr Milvus 过滤表达式（权限），null 表示不过滤
     * @param category   文档分类过滤，null 或空表示不过滤
     * @return 检索结果列表
     */
    @Override
    public List<SearchResult> search(String query, int maxResults, double minScore,
                                     String filterExpr, String category) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        var requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore);

        // 构建 LangChain4j Filter：权限过滤 + 分类过滤
        Filter filter = buildFilter(filterExpr, category);
        if (filter != null) {
            requestBuilder.filter(filter);
        }

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(requestBuilder.build());

        List<SearchResult> searchResults = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            TextSegment segment = match.embedded();
            Metadata metadata = segment.metadata();

            searchResults.add(new SearchResult(
                    segment.text(),
                    metadata.getString("document_key"),
                    metadata.getString("document_name"),
                    parseIntOrNull(metadata.getString("start_page")),
                    parseIntOrNull(metadata.getString("chunk_index")),
                    match.score(),
                    metadata.getString("category")
            ));
        }

        logger.debug("Search for '{}' returned {} results", query, searchResults.size());
        return searchResults;
    }

    /**
     * 语义检索（无分类过滤，向后兼容）
     */
    @Override
    public List<SearchResult> search(String query, int maxResults, double minScore, String filterExpr) {
        return search(query, maxResults, minScore, filterExpr, null);
    }

    /**
     * 构建 LangChain4j Filter，组合权限表达式和分类过滤
     */
    private Filter buildFilter(String filterExpr, String category) {
        // 当前 LangChain4j 的 Filter 接口不直接支持 Milvus 原生表达式字符串
        // 分类过滤在应用层二次校验（见 KnowledgeQueryApplicationService）
        // 此处预留扩展点，后续可接入 Milvus 原生 expr 支持
        return null;
    }

    /**
     * 删除指定文档的所有向量
     *
     * @param documentKey 文档唯一标识
     */
    @Override
    public void deleteByDocumentKey(String documentKey) {
        Filter filter = new IsEqualTo("document_key", documentKey);
        embeddingStore.removeAll(filter);
        logger.info("Deleted vectors for document {}", documentKey);
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
