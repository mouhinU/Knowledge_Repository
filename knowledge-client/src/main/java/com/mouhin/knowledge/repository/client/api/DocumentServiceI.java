package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.client.dto.KnowledgeStatsVO;
import java.util.List;
import java.util.Map;

/**
 * 文档管理应用服务契约（client 层）
 *
 * <p>提供文档查询、统计、归档、删除等管理操作的对外契约，出入参均为 DTO/VO，不暴露领域对象。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public interface DocumentServiceI {

    /**
     * 根据文档标识查询文档详情。
     *
     * @param documentKey 文档唯一标识
     * @return 文档 VO，不存在时返回 null
     */
    DocumentVO getDocument(String documentKey);

    /**
     * 按上传者分页查询文档列表。
     *
     * @param ownerId 上传者用户标识
     * @param page 页码（从 1 开始）
     * @param size 每页条数
     * @return 该上传者的文档 VO 分页列表
     */
    List<DocumentVO> listByOwner(String ownerId, int page, int size);

    /**
     * 按部门分页查询文档列表。
     *
     * @param departmentId 部门标识
     * @param page 页码（从 1 开始）
     * @param size 每页条数
     * @return 该部门下的文档 VO 分页列表
     */
    List<DocumentVO> listByDepartment(String departmentId, int page, int size);

    /**
     * 查询全部文档列表。
     *
     * @return 全部文档 VO 列表
     */
    List<DocumentVO> listDocuments();

    /**
     * 按状态查询文档列表（status 为枚举大写名，适配层已完成合法性校验）。
     *
     * @param status 文档状态枚举名（如 PARSING、INDEXED、ARCHIVED）
     * @return 指定状态的文档 VO 列表
     */
    List<DocumentVO> listByStatus(String status);

    /**
     * 获取知识库统计概览（文档数、分块数、向量化状态等）。
     *
     * @return 知识库统计 VO
     */
    KnowledgeStatsVO getStats();

    /**
     * 获取按分类维度统计的文档数量。
     *
     * @return 分类名称到文档数量的映射
     */
    Map<String, Long> getCategoryStats();

    /**
     * 归档文档（软删除，文档状态变更为 ARCHIVED）。
     *
     * @param documentKey 文档唯一标识
     * @throws IllegalArgumentException 文档不存在
     */
    void archive(String documentKey);

    /**
     * 删除文档（物理删除或标记删除，取决于实现策略）。
     *
     * @param documentKey 文档唯一标识
     * @throws IllegalArgumentException 文档不存在
     */
    void delete(String documentKey);
}
