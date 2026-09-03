package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.service.DepartmentManagementApplicationService;
import com.mouhin.knowledge.repository.domain.model.entity.Department;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 部门管理控制器
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@RestController
@RequestMapping("/api/admin/department")
public class DepartmentAdminController {

    private static final Logger logger = LoggerFactory.getLogger(DepartmentAdminController.class);

    private final DepartmentManagementApplicationService departmentService;

    public DepartmentAdminController(DepartmentManagementApplicationService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        List<Department> depts = departmentService.listAll();
        return ResponseEntity.ok(depts.stream().map(this::toResponse).toList());
    }

    @GetMapping("/tree")
    public ResponseEntity<List<Map<String, Object>>> tree() {
        return ResponseEntity.ok(departmentService.getTree());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String departmentName = (String) body.get("departmentName");
        Long parentId = body.get("parentId") != null ? ((Number) body.get("parentId")).longValue() : null;

        Department dept = departmentService.create(departmentName, parentId);
        return ResponseEntity.ok(toResponse(dept));
    }

    @PutMapping("/{departmentKey}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String departmentKey,
                                                      @RequestBody Map<String, Object> body) {
        String departmentName = (String) body.get("departmentName");
        Long parentId = body.get("parentId") != null ? ((Number) body.get("parentId")).longValue() : null;

        Department dept = departmentService.update(departmentKey, departmentName, parentId);
        return ResponseEntity.ok(toResponse(dept));
    }

    private Map<String, Object> toResponse(Department dept) {
        return Map.of(
                "id", dept.getId(),
                "departmentKey", dept.getDepartmentKey(),
                "departmentName", dept.getDepartmentName(),
                "parentId", dept.getParentId() != null ? dept.getParentId() : 0
        );
    }
}
