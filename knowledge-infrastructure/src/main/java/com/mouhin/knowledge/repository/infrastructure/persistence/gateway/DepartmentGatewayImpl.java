package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.gateway.DepartmentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Department;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DepartmentDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.DepartmentMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 部门仓储实现
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Repository
public class DepartmentGatewayImpl implements DepartmentGateway {

    private final DepartmentMapper departmentMapper;

    public DepartmentGatewayImpl(DepartmentMapper departmentMapper) {
        this.departmentMapper = departmentMapper;
    }

    @Override
    public Optional<Department> findById(Long id) {
        DepartmentDO doObj = departmentMapper.selectById(id);
        return Optional.ofNullable(toDomain(doObj));
    }

    @Override
    public Optional<Department> findByDepartmentKey(String departmentKey) {
        LambdaQueryWrapper<DepartmentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartmentDO::getDepartmentKey, departmentKey);
        DepartmentDO doObj = departmentMapper.selectOne(wrapper);
        return Optional.ofNullable(toDomain(doObj));
    }

    @Override
    public List<Department> listAll() {
        return departmentMapper.selectList(null).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Long> findSelfAndAncestorIds(Long departmentId) {
        List<Long> result = new ArrayList<>();
        Long currentId = departmentId;

        while (currentId != null) {
            result.add(currentId);
            DepartmentDO dept = departmentMapper.selectById(currentId);
            if (dept == null) {
                break;
            }
            currentId = dept.getParentId();
        }

        return result;
    }

    @Override
    public void save(Department department) {
        java.util.Objects.requireNonNull(department, "Department must not be null on save");
        DepartmentDO doObj = toDO(department);
        doObj.setCreateTime(LocalDateTime.now());
        departmentMapper.insert(doObj);
        department.setId(doObj.getId());
    }

    @Override
    public void update(Department department) {
        DepartmentDO doObj = toDO(department);
        departmentMapper.updateById(doObj);
    }

    private Department toDomain(DepartmentDO doObj) {
        if (doObj == null) {
            return null;
        }
        Department dept = new Department();
        dept.setId(doObj.getId());
        dept.setDepartmentKey(doObj.getDepartmentKey());
        dept.setDepartmentName(doObj.getDepartmentName());
        dept.setParentId(doObj.getParentId());
        dept.setCreatedTime(doObj.getCreateTime());
        return dept;
    }

    private DepartmentDO toDO(Department department) {
        if (department == null) {
            return null;
        }
        DepartmentDO doObj = new DepartmentDO();
        doObj.setId(department.getId());
        doObj.setDepartmentKey(department.getDepartmentKey());
        doObj.setDepartmentName(department.getDepartmentName());
        doObj.setParentId(department.getParentId());
        return doObj;
    }
}
