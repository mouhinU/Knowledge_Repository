package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.executor.department.DepartmentCreateCmdExe;
import com.mouhin.knowledge.repository.application.executor.department.DepartmentListQryExe;
import com.mouhin.knowledge.repository.application.executor.department.DepartmentTreeQryExe;
import com.mouhin.knowledge.repository.application.executor.department.DepartmentUpdateCmdExe;
import com.mouhin.knowledge.repository.client.api.DepartmentServiceI;
import com.mouhin.knowledge.repository.client.dto.DepartmentCreateCmd;
import com.mouhin.knowledge.repository.client.dto.DepartmentTreeNodeVO;
import com.mouhin.knowledge.repository.client.dto.DepartmentUpdateCmd;
import com.mouhin.knowledge.repository.client.dto.DepartmentVO;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 部门管理应用服务实现（app 层，仅分发到 Executor）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Service
public class DepartmentServiceImpl implements DepartmentServiceI {

    private final DepartmentListQryExe departmentListQryExe;
    private final DepartmentTreeQryExe departmentTreeQryExe;
    private final DepartmentCreateCmdExe departmentCreateCmdExe;
    private final DepartmentUpdateCmdExe departmentUpdateCmdExe;

    public DepartmentServiceImpl(
            DepartmentListQryExe departmentListQryExe,
            DepartmentTreeQryExe departmentTreeQryExe,
            DepartmentCreateCmdExe departmentCreateCmdExe,
            DepartmentUpdateCmdExe departmentUpdateCmdExe) {
        this.departmentListQryExe = departmentListQryExe;
        this.departmentTreeQryExe = departmentTreeQryExe;
        this.departmentCreateCmdExe = departmentCreateCmdExe;
        this.departmentUpdateCmdExe = departmentUpdateCmdExe;
    }

    @Override
    public List<DepartmentVO> listDepartments() {
        return departmentListQryExe.execute();
    }

    @Override
    public List<DepartmentTreeNodeVO> getDepartmentTree() {
        return departmentTreeQryExe.execute();
    }

    @Override
    public DepartmentVO createDepartment(DepartmentCreateCmd cmd) {
        return departmentCreateCmdExe.execute(cmd);
    }

    @Override
    public DepartmentVO updateDepartment(DepartmentUpdateCmd cmd) {
        return departmentUpdateCmdExe.execute(cmd);
    }
}
