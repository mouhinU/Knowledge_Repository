package com.mouhin.knowledge.repository.application.executor.department;

import com.mouhin.knowledge.repository.application.converter.DepartmentConverter;
import com.mouhin.knowledge.repository.client.dto.DepartmentVO;
import com.mouhin.knowledge.repository.domain.gateway.DepartmentGateway;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 部门列表查询执行器（app 层用例）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class DepartmentListQryExe {

    private final DepartmentGateway departmentGateway;

    public DepartmentListQryExe(DepartmentGateway departmentGateway) {
        this.departmentGateway = departmentGateway;
    }

    public List<DepartmentVO> execute() {
        return departmentGateway.listAll().stream()
                .map(DepartmentConverter::toVO)
                .toList();
    }
}
