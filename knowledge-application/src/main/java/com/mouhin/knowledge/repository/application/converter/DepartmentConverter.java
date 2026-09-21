package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.client.dto.DepartmentTreeNodeVO;
import com.mouhin.knowledge.repository.client.dto.DepartmentVO;
import com.mouhin.knowledge.repository.domain.model.entity.Department;

/**
 * 部门 领域实体 → 视图对象 转换器（app 层）
 *
 * <p>字段映射与既有 {@code DepartmentAdminController.toResponse} 完全一致，保证 REST JSON 结构不变：扁平 VO 的 parentId 为
 * null 时归一为 0；树节点 VO 保留原始 parentId（可为 null）。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public final class DepartmentConverter {

    private DepartmentConverter() {}

    /** 领域实体 → 扁平 VO（parentId null 归一为 0） */
    public static DepartmentVO toVO(Department dept) {
        DepartmentVO vo = new DepartmentVO();
        vo.setId(dept.getId());
        vo.setDepartmentKey(dept.getDepartmentKey());
        vo.setDepartmentName(dept.getDepartmentName());
        vo.setParentId(dept.getParentId() != null ? dept.getParentId() : 0L);
        return vo;
    }

    /** 领域实体 → 树节点 VO（children 由调用方组装） */
    public static DepartmentTreeNodeVO toTreeNode(Department dept) {
        DepartmentTreeNodeVO node = new DepartmentTreeNodeVO();
        node.setId(dept.getId());
        node.setDepartmentKey(dept.getDepartmentKey());
        node.setDepartmentName(dept.getDepartmentName());
        node.setParentId(dept.getParentId());
        return node;
    }
}
