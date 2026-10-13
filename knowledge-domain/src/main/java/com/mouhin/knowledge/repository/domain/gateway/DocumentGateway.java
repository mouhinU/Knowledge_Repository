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

    /**
     * 保存文档（新增），主键回填到入参实体。
     *
     * @param document 文档聚合根
     * @return 持久化后的文档（含回填主键）
     */
    Document save(Document document);

    /**
     * 更新文档。
     *
     * @param document 文档聚合根（须包含有效主键）
     */
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

    /**
     * 根据主键查找文档。
     *
     * @param id 文档主键
     * @return 命中的文档；不存在时 {@link Optional#empty()}
     */
    Optional<Document> findById(Long id);

    /**
     * 根据文档业务键查找文档。
     *
     * @param documentKey 文档业务键
     * @return 命中的文档；不存在时 {@link Optional#empty()}
     */
    Optional<Document> findByDocumentKey(String documentKey);

    /**
     * 根据 documentKey 集合批量查找，返回 documentKey → Document 映射。
     *
     * <p>用于检索链路消除 N+1 查询：单次 SQL IN 查询替代逐条 findByDocumentKey。 缺失的 key 不出现在 Map 中（调用方按最小权限拒绝）。
     *
     * @param documentKeys 文档 key 集合
     * @return documentKey → Document 映射
     */
    Map<String, Document> findByDocumentKeys(List<String> documentKeys);

    /**
     * 根据文件校验和查找文档（用于去重）。
     *
     * @param fileChecksum 文件 MD5 校验和
     * @return 命中的文档；不存在时 {@link Optional#empty()}
     */
    Optional<Document> findByFileChecksum(String fileChecksum);

    /**
     * 根据状态查找文档列表。
     *
     * @param status 文档状态
     * @return 符合状态的文档列表
     */
    List<Document> listByStatus(DocumentStatusEnum status);

    /**
     * 查询全部文档列表。
     *
     * @return 全部文档列表
     */
    List<Document> listAll();

    /**
     * 根据所有者查找文档列表（分页）。
     *
     * @param ownerId 所有者标识
     * @param page 页码
     * @param size 每页数量
     * @return 该所有者的文档列表
     */
    List<Document> listByOwnerId(String ownerId, int page, int size);

    /**
     * 根据部门查找文档列表（分页）。
     *
     * @param departmentId 部门标识
     * @param page 页码
     * @param size 每页数量
     * @return 该部门下的文档列表
     */
    List<Document> listByDepartmentId(String departmentId, int page, int size);

    /**
     * 统计指定状态的文档数量。
     *
     * @param status 文档状态
     * @return 文档数量
     */
    long countByStatus(DocumentStatusEnum status);

    /**
     * 按分类统计文档数量。
     *
     * @return 分类名 → 文档数量 映射
     */
    Map<String, Long> countByCategory();

    /**
     * 根据主键删除文档。
     *
     * @param id 文档主键
     */
    void deleteById(Long id);
}
