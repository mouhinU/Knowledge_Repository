package com.mouhin.knowledge.repository.domain.repository;

import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文档仓储接口
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
public interface DocumentRepository {

    /**
     * 保存文档（新增）
     */
    Document save(Document document);

    /**
     * 更新文档
     */
    void update(Document document);

    /**
     * 根据 ID 查找
     */
    Optional<Document> findById(Long id);

    /**
     * 根据 documentKey 查找
     */
    Optional<Document> findByDocumentKey(String documentKey);

    /**
     * 根据文件校验和查找（用于去重）
     */
    Optional<Document> findByFileChecksum(String fileChecksum);

    /**
     * 根据状态查找文档列表
     */
    List<Document> listByStatus(DocumentStatusEnum status);

    /**
     * 查询全部文档列表
     */
    List<Document> listAll();

    /**
     * 根据所有者查找文档列表
     */
    List<Document> listByOwnerId(String ownerId, int page, int size);

    /**
     * 根据部门查找文档列表
     */
    List<Document> listByDepartmentId(String departmentId, int page, int size);

    /**
     * 统计文档数量
     */
    long countByStatus(DocumentStatusEnum status);

    /**
     * 按分类统计文档数量
     */
    Map<String, Long> countByCategory();

    /**
     * 删除文档
     */
    void deleteById(Long id);
}
