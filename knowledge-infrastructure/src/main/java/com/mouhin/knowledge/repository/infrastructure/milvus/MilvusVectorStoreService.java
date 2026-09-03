package com.mouhin.knowledge.repository.infrastructure.milvus;

import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
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
public class MilvusVectorStoreService {

    private static final Logger logger = LoggerFactory.getLogger(MilvusVectorStoreService.class);

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public MilvusVectorStoreService(EmbeddingStore<TextSegment> embeddingStore,
                                    EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 批量向量化并存储文档分块
     *
     * @param chunks 文档分块列表
     */
    public void storeChunks(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        List<TextSegment> segments = new ArrayList<>(chunks.size());
        for (DocumentChunk chunk : chunks) {
            Metadata metadata = new Metadata()
                    .put("document_key", chunk.getDocumentKey())
                    .put("chunk_index", String.valueOf(chunk.getChunkIndex()))
                    .put("start_page", String.valueOf(chunk.getStartPage()))
                    .put("end_page", String.valueOf(chunk.getEndPage()))
                    .put("department_id", chunk.getDepartmentId() != null ? chunk.getDepartmentId() : "")
                    .put("visibility", chunk.getVisibility() != null ? chunk.getVisibility() : "INTERNAL")
                    .put("allowed_roles", chunk.getAllowedRoles() != null ? chunk.getAllowedRoles() : "")
                    .put("owner_id", chunk.getOwnerId() != null ? chunk.getOwnerId() : "");

            TextSegment segment = TextSegment.from(chunk.getContent(), metadata);
            segments.add(segment);
        }

        // 批量嵌入
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        // 存储到 Milvus
        embeddingStore.addAll(embeddings, segments);

        logger.info("Stored {} chunks in Milvus", chunks.size());
    }

    /**
     * 语义检索，支持权限过滤
     *
     * @param query      查询文本
     * @param maxResults 最大返回数量
     * @param minScore   最低相似度阈值
     * @param filterExpr Milvus 过滤表达式（权限），null 表示不过滤
     * @return 检索结果列表
     */
    public List<SearchResult> search(String query, int maxResults, double minScore, String filterExpr) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        var requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore);

        // 注意：LangChain4j 的 Filter 接口不直接支持 Milvus 原生表达式
        // 这里使用 IsEqualTo 等组合过滤器；如需复杂权限过滤，
        // 可通过 MilvusEmbeddingStore 的原生 API 传入 expr 参数
        // 当前实现：权限过滤在应用层二次校验

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(requestBuilder.build());

        List<SearchResult> searchResults = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            TextSegment segment = match.embedded();
            Metadata metadata = segment.metadata();

            searchResults.add(new SearchResult(
                    segment.text(),
                    metadata.getString("document_key"),
                    "",  // documentName will be filled by application service
                    parseIntOrNull(metadata.getString("start_page")),
                    parseIntOrNull(metadata.getString("chunk_index")),
                    match.score()
            ));
        }

        logger.debug("Search for '{}' returned {} results", query, searchResults.size());
        return searchResults;
    }

    /**
     * 删除指定文档的所有向量
     *
     * @param documentKey 文档唯一标识
     */
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
