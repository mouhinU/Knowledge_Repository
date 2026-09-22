package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文档仓储接口
 *
 * @author mouhinU
 * @date 2026-09-02
 */
public interface DocumentGateway {

    /** 保存文档（新增） */
    Document save(Document document);

    /** 更新文档 */
    void update(Document document);

    /**
     * 清空文档的错误信息列（置为 NULL）。
     *
     * <p>MyBatis-Plus 默认 {@code FieldStrategy=NOT_NULL} 会使 {@code updateById} 跳过值为 null 的字段，
     * 因此重新入库（reindex）把上一轮的 {@code error_message} 置 null 后走 {@code update} 并不会真正清列，
     * 残留的旧错误信息会误导前端。此方法用专用 update-wrapper 显式将 error_message 写回 NULL。 凡"清空某列"一律走此类专用通道，勿退回
     * updateById。
     *
     * @param documentId 文档主键
     */
    void clearErrorMessage(Long documentId);

    /** 根据 ID 查找 */
    Optional<Document> findById(Long id);

    /** 根据 documentKey 查找 */
    Optional<Document> findByDocumentKey(String documentKey);

    /** 根据文件校验和查找（用于去重） */
    Optional<Document> findByFileChecksum(String fileChecksum);

    /** 根据状态查找文档列表 */
    List<Document> listByStatus(DocumentStatusEnum status);

    /** 查询全部文档列表 */
    List<Document> listAll();

    /** 根据所有者查找文档列表 */
    List<Document> listByOwnerId(String ownerId, int page, int size);

    /** 根据部门查找文档列表 */
    List<Document> listByDepartmentId(String departmentId, int page, int size);

    /** 统计文档数量 */
    long countByStatus(DocumentStatusEnum status);

    /** 按分类统计文档数量 */
    Map<String, Long> countByCategory();

    /** 删除文档 */
    void deleteById(Long id);
}
