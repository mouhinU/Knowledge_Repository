package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import java.util.List;
import java.util.Optional;

/**
 * 文档分块仓储接口
 *
 * @author mouhinU
 * @date 2026-09-02
 */
public interface DocumentChunkGateway {

    /**
     * 批量保存文档分块。
     *
     * @param chunks 分块列表
     */
    void saveBatch(List<DocumentChunk> chunks);

    /**
     * 根据文档 ID 查找所有分块。
     *
     * @param documentId 文档主键
     * @return 该文档下的全部分块列表
     */
    List<DocumentChunk> listByDocumentId(Long documentId);

    /**
     * 根据分块业务键查找单个分块。
     *
     * @param chunkKey 分块业务键
     * @return 命中的分块；不存在时 {@link Optional#empty()}
     */
    Optional<DocumentChunk> findByChunkKey(String chunkKey);

    /**
     * 根据文档 ID 删除该文档下的所有分块。
     *
     * @param documentId 文档主键
     */
    void deleteByDocumentId(Long documentId);

    /**
     * 统计文档的分块数量。
     *
     * @param documentId 文档主键
     * @return 分块数量
     */
    long countByDocumentId(Long documentId);
}
