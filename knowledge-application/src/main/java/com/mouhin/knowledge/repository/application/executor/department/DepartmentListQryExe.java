package com.mouhin.knowledge.repository.application.executor.department;

import com.mouhin.knowledge.repository.application.converter.DepartmentConverter;
import com.mouhin.knowledge.repository.client.dto.DepartmentVO;
import com.mouhin.knowledge.repository.domain.gateway.DepartmentGateway;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 部门列表查询执行器（app 层用例）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class DepartmentListQryExe {

    private final DepartmentGateway departmentGateway;

    public DepartmentListQryExe(DepartmentGateway departmentGateway) {
        this.departmentGateway = departmentGateway;
    }

    public List<DepartmentVO> execute() {
        return departmentGateway.listAll().stream().map(DepartmentConverter::toVO).toList();
    }
}
