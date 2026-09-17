package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.Department;

import java.util.List;
import java.util.Optional;

/**
 * 部门仓储接口
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
public interface DepartmentGateway {

    Optional<Department> findById(Long id);

    Optional<Department> findByDepartmentKey(String departmentKey);

    List<Department> listAll();

    /**
     * 查找指定部门及其所有祖先部门的 ID 列表（含自身）
     */
    List<Long> findSelfAndAncestorIds(Long departmentId);

    void save(Department department);

    void update(Department department);
}
