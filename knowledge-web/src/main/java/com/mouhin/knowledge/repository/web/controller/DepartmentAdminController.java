package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.client.api.DepartmentServiceI;
import com.mouhin.knowledge.repository.client.dto.DepartmentCreateCmd;
import com.mouhin.knowledge.repository.client.dto.DepartmentTreeNodeVO;
import com.mouhin.knowledge.repository.client.dto.DepartmentUpdateCmd;
import com.mouhin.knowledge.repository.client.dto.DepartmentVO;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 部门管理控制器（adapter 层）
 *
 * <p>仅负责请求适配：解析入参组装 Command，调用 app 层 {@link DepartmentServiceI}， 把返回的 VO 直接作为 REST 响应体（JSON
 * 结构与既有前端契约保持一致）。
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@RestController
@RequestMapping("/api/admin/department")
@Slf4j
public class DepartmentAdminController {

    /** 请求体 map 的父部门字段名（java:S1192 抽常量防 4 处漂移）。 */
    private static final String FIELD_PARENT_ID = "parentId";

    private final DepartmentServiceI departmentService;

    public DepartmentAdminController(DepartmentServiceI departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public ResponseEntity<List<DepartmentVO>> list() {
        return ResponseEntity.ok(departmentService.listDepartments());
    }

    @GetMapping("/tree")
    public ResponseEntity<List<DepartmentTreeNodeVO>> tree() {
        return ResponseEntity.ok(departmentService.getDepartmentTree());
    }

    @PostMapping
    public ResponseEntity<DepartmentVO> create(@RequestBody Map<String, Object> body) {
        DepartmentCreateCmd cmd = new DepartmentCreateCmd();
        cmd.setDepartmentName((String) body.get("departmentName"));
        cmd.setParentId(
                body.get(FIELD_PARENT_ID) != null
                        ? ((Number) body.get(FIELD_PARENT_ID)).longValue()
                        : null);

        return ResponseEntity.ok(departmentService.createDepartment(cmd));
    }

    @PutMapping("/{departmentKey}")
    public ResponseEntity<DepartmentVO> update(
            @PathVariable String departmentKey, @RequestBody Map<String, Object> body) {
        DepartmentUpdateCmd cmd = new DepartmentUpdateCmd();
        cmd.setDepartmentKey(departmentKey);
        cmd.setDepartmentName((String) body.get("departmentName"));
        cmd.setParentId(
                body.get(FIELD_PARENT_ID) != null
                        ? ((Number) body.get(FIELD_PARENT_ID)).longValue()
                        : null);

        return ResponseEntity.ok(departmentService.updateDepartment(cmd));
    }
}
