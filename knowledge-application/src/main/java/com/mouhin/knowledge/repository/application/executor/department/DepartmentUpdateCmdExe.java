package com.mouhin.knowledge.repository.application.executor.department;

import com.mouhin.knowledge.repository.application.converter.DepartmentConverter;
import com.mouhin.knowledge.repository.client.dto.DepartmentUpdateCmd;
import com.mouhin.knowledge.repository.client.dto.DepartmentVO;
import com.mouhin.knowledge.repository.domain.gateway.DepartmentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Department;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 修改部门命令执行器（app 层用例，事务边界）
 *
 * <p>departmentName / parentId 为 null 时保持原值不变，沿用既有更新语义。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class DepartmentUpdateCmdExe {

    private final DepartmentGateway departmentGateway;

    public DepartmentUpdateCmdExe(DepartmentGateway departmentGateway) {
        this.departmentGateway = departmentGateway;
    }

    @Transactional
    public DepartmentVO execute(DepartmentUpdateCmd cmd) {
        Department dept = departmentGateway.findByDepartmentKey(cmd.getDepartmentKey())
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + cmd.getDepartmentKey()));
        if (cmd.getDepartmentName() != null) {
            dept.setDepartmentName(cmd.getDepartmentName());
        }
        if (cmd.getParentId() != null) {
            dept.setParentId(cmd.getParentId());
        }
        departmentGateway.update(dept);
        return DepartmentConverter.toVO(dept);
    }
}
