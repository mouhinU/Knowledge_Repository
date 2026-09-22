package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.domain.service.IndexProgressCallback;
import java.util.List;

/**
 * 向量存储网关（领域层端口，由基础设施层 Milvus 实现）
 *
 * <p>屏蔽具体向量库（Milvus / LangChain4j EmbeddingStore）技术细节，应用层仅依赖本接口， 出入参均为领域对象。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public interface VectorStoreGateway {

    /** 存储文档分块向量 */
    void storeChunks(List<DocumentChunk> chunks);

    /** 存储文档分块向量（带进度回调） */
    void storeChunks(List<DocumentChunk> chunks, IndexProgressCallback callback);

    /** 语义检索（带权限过滤表达式与分类过滤） */
    List<SearchResult> search(
            String query, int maxResults, double minScore, String filterExpr, String category);

    /** 语义检索（无分类过滤，向后兼容） */
    List<SearchResult> search(String query, int maxResults, double minScore, String filterExpr);

    /** 删除指定文档的所有向量 */
    void deleteByDocumentKey(String documentKey);
}
