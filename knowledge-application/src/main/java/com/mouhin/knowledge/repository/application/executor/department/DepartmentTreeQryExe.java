package com.mouhin.knowledge.repository.application.executor.department;

import com.mouhin.knowledge.repository.application.converter.DepartmentConverter;
import com.mouhin.knowledge.repository.client.dto.DepartmentTreeNodeVO;
import com.mouhin.knowledge.repository.domain.gateway.DepartmentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Department;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 部门树查询执行器（app 层用例）
 *
 * <p>装配逻辑与原 {@code DepartmentManagementApplicationService.getTree()} 一致： 先为每个部门建节点（保持 listAll
 * 顺序），再按 parentId 挂接子节点；父不存在的节点视为根。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class DepartmentTreeQryExe {

    private final DepartmentGateway departmentGateway;

    public DepartmentTreeQryExe(DepartmentGateway departmentGateway) {
        this.departmentGateway = departmentGateway;
    }

    public List<DepartmentTreeNodeVO> execute() {
        List<Department> allDepts = departmentGateway.listAll();
        Map<Long, DepartmentTreeNodeVO> nodeMap = new LinkedHashMap<>();
        Map<Long, Long> parentMap = new LinkedHashMap<>();

        for (Department dept : allDepts) {
            DepartmentTreeNodeVO node = DepartmentConverter.toTreeNode(dept);
            nodeMap.put(dept.getId(), node);
            parentMap.put(dept.getId(), dept.getParentId());
        }

        List<DepartmentTreeNodeVO> tree = new ArrayList<>();
        for (Map.Entry<Long, DepartmentTreeNodeVO> entry : nodeMap.entrySet()) {
            Long parentId = parentMap.get(entry.getKey());
            DepartmentTreeNodeVO node = entry.getValue();
            if (parentId == null || !nodeMap.containsKey(parentId)) {
                tree.add(node);
            } else {
                nodeMap.get(parentId).getChildren().add(node);
            }
        }
        return tree;
    }
}
