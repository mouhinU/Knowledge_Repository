package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.TenantCreateCmd;
import com.mouhin.knowledge.repository.client.dto.TenantProvisionCmd;
import com.mouhin.knowledge.repository.client.dto.TenantProvisionVO;
import com.mouhin.knowledge.repository.client.dto.TenantStatusCmd;
import com.mouhin.knowledge.repository.client.dto.TenantVO;
import java.util.List;

/**
 * 平台端租户生命周期应用服务契约（client 层对外接口，P3-T1）。
 *
 * <p>实现位于 app 层 {@code PlatformTenantServiceImpl}，仅做分发到各用例 Executor（COLA 红线 #8）。 面向 {@code
 * /api/platform/**} 平台管理员端点，与租户管理员侧的 {@code DepartmentServiceI} 等契约分属不同鉴权体系（T-4 方案 B）。
 *
 * <p>P3-T1 交付 create / suspend / resume / get / list；P3-T2 叠加 provision（开通状态机）。 archive / delete
 * 的完整编排属 P3-T5，暂不在契约内。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
public interface PlatformTenantServiceI {

    /** 创建租户（落 PENDING + 生成 UUID v4 provisionRequestId；开通由 {@link #provisionTenant} 驱动）。 */
    TenantVO createTenant(TenantCreateCmd cmd);

    /** 开通租户（PENDING/FAILED → PROVISIONING → ACTIVE/FAILED，步骤级幂等可重放，P3-T2）。 */
    TenantProvisionVO provisionTenant(TenantProvisionCmd cmd);

    /** 软停租户（ACTIVE → SUSPENDED）。 */
    TenantVO suspendTenant(TenantStatusCmd cmd);

    /** 恢复租户（SUSPENDED → ACTIVE）。 */
    TenantVO resumeTenant(TenantStatusCmd cmd);

    /** 按业务键查询单个租户。 */
    TenantVO getTenant(String tenantKey);

    /** 列出全部租户。 */
    List<TenantVO> listTenants();
}
