package com.mouhin.knowledge.repository.application.executor.department;

import com.mouhin.knowledge.repository.application.converter.DepartmentConverter;
import com.mouhin.knowledge.repository.client.dto.DepartmentCreateCmd;
import com.mouhin.knowledge.repository.client.dto.DepartmentVO;
import com.mouhin.knowledge.repository.domain.gateway.DepartmentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Department;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 新增部门命令执行器（app 层用例，事务边界）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
@Slf4j
public class DepartmentCreateCmdExe {

    private final DepartmentGateway departmentGateway;

    public DepartmentCreateCmdExe(DepartmentGateway departmentGateway) {
        this.departmentGateway = departmentGateway;
    }

    @Transactional
    public DepartmentVO execute(DepartmentCreateCmd cmd) {
        Department dept = new Department();
        dept.setDepartmentKey(UUID.randomUUID().toString());
        dept.setDepartmentName(cmd.getDepartmentName());
        dept.setParentId(cmd.getParentId());
        departmentGateway.save(dept);

        log.info("Department created: {} ({})", cmd.getDepartmentName(), dept.getDepartmentKey());
        return DepartmentConverter.toVO(dept);
    }
}
