package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.domain.model.entity.Department;
import com.mouhin.knowledge.repository.domain.repository.DepartmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 部门管理应用服务
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Service
public class DepartmentManagementApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(DepartmentManagementApplicationService.class);

    private final DepartmentRepository departmentRepository;

    public DepartmentManagementApplicationService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public List<Department> listAll() {
        return departmentRepository.listAll();
    }

    /**
     * 获取部门树结构
     */
    public List<Map<String, Object>> getTree() {
        List<Department> allDepts = departmentRepository.listAll();
        Map<Long, Map<String, Object>> nodeMap = new LinkedHashMap<>();

        for (Department dept : allDepts) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", dept.getId());
            node.put("departmentKey", dept.getDepartmentKey());
            node.put("departmentName", dept.getDepartmentName());
            node.put("parentId", dept.getParentId());
            node.put("children", new ArrayList<>());
            nodeMap.put(dept.getId(), node);
        }

        List<Map<String, Object>> tree = new ArrayList<>();
        for (Map<String, Object> node : nodeMap.values()) {
            Long parentId = (Long) node.get("parentId");
            if (parentId == null || !nodeMap.containsKey(parentId)) {
                tree.add(node);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) nodeMap.get(parentId).get("children");
                children.add(node);
            }
        }

        return tree;
    }

    public Department getByKey(String departmentKey) {
        return departmentRepository.findByDepartmentKey(departmentKey)
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + departmentKey));
    }

    @Transactional
    public Department create(String departmentName, Long parentId) {
        Department dept = new Department();
        dept.setDepartmentKey(UUID.randomUUID().toString());
        dept.setDepartmentName(departmentName);
        dept.setParentId(parentId);
        departmentRepository.save(dept);

        logger.info("Department created: {} ({})", departmentName, dept.getDepartmentKey());
        return dept;
    }

    @Transactional
    public Department update(String departmentKey, String departmentName, Long parentId) {
        Department dept = getByKey(departmentKey);
        if (departmentName != null) {
            dept.setDepartmentName(departmentName);
        }
        if (parentId != null) {
            dept.setParentId(parentId);
        }
        departmentRepository.update(dept);
        return dept;
    }
}
