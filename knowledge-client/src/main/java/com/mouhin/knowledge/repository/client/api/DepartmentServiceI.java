package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.DepartmentCreateCmd;
import com.mouhin.knowledge.repository.client.dto.DepartmentTreeNodeVO;
import com.mouhin.knowledge.repository.client.dto.DepartmentUpdateCmd;
import com.mouhin.knowledge.repository.client.dto.DepartmentVO;
import java.util.List;

/**
 * 部门管理应用服务契约（client 层对外接口）
 *
 * <p>实现位于 app 层 {@code DepartmentServiceImpl}，仅做分发到各用例 Executor。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public interface DepartmentServiceI {

    /**
     * 查询部门平铺列表。
     *
     * @return 全部部门 VO 列表
     */
    List<DepartmentVO> listDepartments();

    /**
     * 获取部门树形结构。
     *
     * @return 部门树节点列表（含子节点嵌套）
     */
    List<DepartmentTreeNodeVO> getDepartmentTree();

    /**
     * 新增部门。
     *
     * @param cmd 部门创建命令，包含部门名称、上级部门 ID 等信息
     * @return 创建后的部门 VO
     */
    DepartmentVO createDepartment(DepartmentCreateCmd cmd);

    /**
     * 修改部门信息。
     *
     * @param cmd 部门更新命令，包含部门 ID 与待更新字段
     * @return 更新后的部门 VO
     * @throws IllegalArgumentException 部门不存在或参数非法
     */
    DepartmentVO updateDepartment(DepartmentUpdateCmd cmd);
}
