package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;

import java.util.List;
import java.util.Optional;

/**
 * 文档分块仓储接口
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
public interface DocumentChunkGateway {

    /**
     * 批量保存分块
     */
    void saveBatch(List<DocumentChunk> chunks);

    /**
     * 根据文档 ID 查找所有分块
     */
    List<DocumentChunk> listByDocumentId(Long documentId);

    /**
     * 根据 chunkKey 查找
     */
    Optional<DocumentChunk> findByChunkKey(String chunkKey);

    /**
     * 根据文档 ID 删除所有分块
     */
    void deleteByDocumentId(Long documentId);

    /**
     * 统计文档的分块数量
     */
    long countByDocumentId(Long documentId);
}
