package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.DepartmentCreateCmd;
import com.mouhin.knowledge.repository.client.dto.DepartmentTreeNodeVO;
import com.mouhin.knowledge.repository.client.dto.DepartmentUpdateCmd;
import com.mouhin.knowledge.repository.client.dto.DepartmentVO;

import java.util.List;

/**
 * 部门管理应用服务契约（client 层对外接口）
 *
 * <p>实现位于 app 层 {@code DepartmentServiceImpl}，仅做分发到各用例 Executor。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public interface DepartmentServiceI {

    /**
     * 部门列表
     */
    List<DepartmentVO> listDepartments();

    /**
     * 部门树
     */
    List<DepartmentTreeNodeVO> getDepartmentTree();

    /**
     * 新增部门
     */
    DepartmentVO createDepartment(DepartmentCreateCmd cmd);

    /**
     * 修改部门
     */
    DepartmentVO updateDepartment(DepartmentUpdateCmd cmd);
}
