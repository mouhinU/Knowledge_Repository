package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.Department;
import java.util.List;
import java.util.Optional;

/**
 * 部门仓储接口。
 *
 * <p><b>P3-T3 扩展</b>：新增 {@link #saveWithTenant(Department, long)} 与 {@link
 * #findByDepartmentKeyAndTenant(String, long)}，服务于"平台端开通新租户时建根部门"场景。
 *
 * @author mouhinU
 * @date 2026-09-02
 */
public interface DepartmentGateway {

    /**
     * 按主键查找部门。
     *
     * @param id 部门主键
     * @return 命中的部门；不存在时 {@link Optional#empty()}
     */
    Optional<Department> findById(Long id);

    /**
     * 按部门业务键查找部门。
     *
     * @param departmentKey 部门业务键
     * @return 命中的部门；不存在时 {@link Optional#empty()}
     */
    Optional<Department> findByDepartmentKey(String departmentKey);

    /**
     * 按 (departmentKey, tenantId) 精确查找（P3-T3 幂等前置查重）。
     *
     * @param departmentKey 部门业务键
     * @param tenantId 目标租户 id
     * @return 命中的部门；不存在时 {@link Optional#empty()}
     */
    Optional<Department> findByDepartmentKeyAndTenant(String departmentKey, long tenantId);

    /**
     * 查询全部部门列表。
     *
     * @return 全部部门列表
     */
    List<Department> listAll();

    /**
     * 查找指定部门及其所有祖先部门的 ID 列表（含自身）。
     *
     * @param departmentId 部门主键
     * @return 从根部门到指定部门（含自身）的 ID 列表
     */
    List<Long> findSelfAndAncestorIds(Long departmentId);

    /**
     * 保存部门（新增），主键回填 {@code department.id}。
     *
     * @param department 部门实体
     */
    void save(Department department);

    /**
     * 以显式租户 id 落库新部门（P3-T3 根部门创建路径）。
     *
     * <p>行为等同 {@link #save(Department)}，但强制写入指定 {@code tenantId}，不依赖 {@code TenantContext} /
     * MyBatis-Plus 拦截器；主键回填 {@code department.id}。
     *
     * @param department 部门实体
     * @param tenantId 目标租户 id
     */
    void saveWithTenant(Department department, long tenantId);

    /**
     * 更新部门信息。
     *
     * @param department 部门实体（须包含有效主键）
     */
    void update(Department department);
}
