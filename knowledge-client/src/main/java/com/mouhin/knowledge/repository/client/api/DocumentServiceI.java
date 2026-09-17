package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.client.dto.KnowledgeStatsVO;

import java.util.List;
import java.util.Map;

/**
 * 文档管理应用服务契约（client 层）
 *
 * <p>提供文档查询、统计、归档、删除等管理操作的对外契约，出入参均为 DTO/VO，不暴露领域对象。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public interface DocumentServiceI {

    DocumentVO getDocument(String documentKey);

    List<DocumentVO> listByOwner(String ownerId, int page, int size);

    List<DocumentVO> listByDepartment(String departmentId, int page, int size);

    List<DocumentVO> listDocuments();

    /**
     * 按状态查询（status 为枚举大写名，适配层已完成合法性校验）。
     */
    List<DocumentVO> listByStatus(String status);

    KnowledgeStatsVO getStats();

    Map<String, Long> getCategoryStats();

    void archive(String documentKey);

    void delete(String documentKey);
}
