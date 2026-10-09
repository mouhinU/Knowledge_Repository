package com.mouhin.knowledge.repository.application.provisioning.step;

import com.mouhin.knowledge.repository.domain.gateway.DepartmentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Department;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.provisioning.ProvisioningContext;
import com.mouhin.knowledge.repository.domain.provisioning.ProvisioningStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 建租户根部门步骤（P3-T3，order=300）。
 *
 * <p>为新租户建一个 {@code parent_id=null} 的顶级部门，供后续部门树 / 首位 admin 归属使用。执行计划 §5 P3-T3
 * 验收口径之一：新租户开通后应存在根部门行。
 *
 * <p><b>departmentKey 命名</b>：{@code {tenantKey}-root}（如 {@code acme-root}）。
 *
 * <ul>
 *   <li>与 {@code DepartmentCreateCmdExe} 的 UUID 命名不同——本处刻意用可预测 key，便于运维 / 集成测试直接断言。
 *   <li>{@code sys_department.department_key} 现有 {@code idx_dept_key}（非唯一）；租户内唯一性由 {@link
 *       DepartmentGateway#findByDepartmentKeyAndTenant} 前置查重保证，不依赖 DB 约束。
 * </ul>
 *
 * <p><b>幂等</b>：先 {@code findByDepartmentKeyAndTenant} 命中即返回；未命中才 {@code saveWithTenant}。 重放安全。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Component
public class CreateRootDepartmentStep implements ProvisioningStep {

    private static final Logger log = LoggerFactory.getLogger(CreateRootDepartmentStep.class);

    /** 步骤名常量。 */
    public static final String STEP_NAME = "CREATE_ROOT_DEPARTMENT";

    /** 排序权重：在 sys_config 拷贝之后、首位 admin 创建之前。 */
    public static final int STEP_ORDER = 300;

    /** 根部门 key 后缀，与 tenantKey 拼接：{@code acme + "-root" = "acme-root"}。 */
    public static final String ROOT_DEPARTMENT_KEY_SUFFIX = "-root";

    /** 根部门显示名后缀，与 tenant.name 拼接：{@code "Acme 制造" + " 根部门"}。 */
    public static final String ROOT_DEPARTMENT_NAME_SUFFIX = " 根部门";

    private final DepartmentGateway departmentGateway;

    public CreateRootDepartmentStep(DepartmentGateway departmentGateway) {
        this.departmentGateway = departmentGateway;
    }

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public int order() {
        return STEP_ORDER;
    }

    @Override
    public void execute(ProvisioningContext context) {
        Long tenantId = context.tenantId();
        String tenantKey = context.tenantKey();
        if (tenantId == null || tenantKey == null || tenantKey.isBlank()) {
            throw new IllegalStateException("Tenant id/key required to create root department");
        }
        String rootKey = tenantKey + ROOT_DEPARTMENT_KEY_SUFFIX;

        // 幂等前置查
        if (departmentGateway.findByDepartmentKeyAndTenant(rootKey, tenantId).isPresent()) {
            log.info(
                    "[Provisioning] CreateRootDepartment skipped: '{}' already exists under tenant {}",
                    rootKey,
                    tenantId);
            return;
        }

        Tenant tenant = context.tenant();
        String displayName = tenant == null ? null : tenant.getName();
        String deptName =
                (displayName == null || displayName.isBlank())
                        ? tenantKey + ROOT_DEPARTMENT_NAME_SUFFIX
                        : displayName + ROOT_DEPARTMENT_NAME_SUFFIX;

        Department root = new Department();
        root.setDepartmentKey(rootKey);
        root.setDepartmentName(deptName);
        root.setParentId(null);
        departmentGateway.saveWithTenant(root, tenantId);

        log.info(
                "[Provisioning] CreateRootDepartment done: tenant={} deptKey={} deptId={} name='{}'",
                tenantKey,
                rootKey,
                root.getId(),
                deptName);
    }
}
