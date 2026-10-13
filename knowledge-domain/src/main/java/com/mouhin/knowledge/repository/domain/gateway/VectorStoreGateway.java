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

    /**
     * 存储文档分块向量。
     *
     * @param chunks 待存储的文档分块列表
     */
    void storeChunks(List<DocumentChunk> chunks);

    /**
     * 存储文档分块向量（带进度回调）。
     *
     * @param chunks 待存储的文档分块列表
     * @param callback 索引进度回调
     */
    void storeChunks(List<DocumentChunk> chunks, IndexProgressCallback callback);

    /**
     * 语义检索（带权限过滤表达式与分类过滤）。
     *
     * @param query 查询文本
     * @param maxResults 最大返回结果数
     * @param minScore 最低相似度阈值
     * @param filterExpr 权限过滤表达式
     * @param category 文档分类过滤
     * @return 检索结果列表；无命中返回空列表
     */
    List<SearchResult> search(
            String query, int maxResults, double minScore, String filterExpr, String category);

    /**
     * 语义检索（无分类过滤，向后兼容）。
     *
     * @param query 查询文本
     * @param maxResults 最大返回结果数
     * @param minScore 最低相似度阈值
     * @param filterExpr 权限过滤表达式
     * @return 检索结果列表；无命中返回空列表
     */
    List<SearchResult> search(String query, int maxResults, double minScore, String filterExpr);

    /**
     * 删除指定文档的所有向量。
     *
     * @param documentKey 文档业务键
     */
    void deleteByDocumentKey(String documentKey);

    /**
     * 删除指定租户的所有向量（P3-T5 租户硬删，按 metadata {@code tenant_id} 谓词全量移除）。
     *
     * <p>与 Phase 2「S-2 方案 b：JSON 谓词路径」写入侧对齐——{@code tenant_id} 以数值 long 存于 metadata， 本方法按等值谓词
     * {@code removeAll}。操作幂等：租户无向量时为无副作用空操作。
     *
     * @param tenantId 数值租户主键（{@code sys_tenant.tenant_id}）
     */
    void deleteByTenantId(long tenantId);
}
