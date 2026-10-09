package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.TenantArchiveCmd;
import com.mouhin.knowledge.repository.client.dto.TenantCreateCmd;
import com.mouhin.knowledge.repository.client.dto.TenantDeleteApprovalCmd;
import com.mouhin.knowledge.repository.client.dto.TenantDeleteApprovalVO;
import com.mouhin.knowledge.repository.client.dto.TenantDeleteCmd;
import com.mouhin.knowledge.repository.client.dto.TenantDeletionPreviewVO;
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
 * <p>P3-T1 交付 create / suspend / resume / get / list；P3-T2 叠加 provision（开通状态机）。 P3-T5 叠加 archive /
 * issueDeleteApproval / confirmDeletion / previewDeletion（S-4=方案 A 归档 + 30 天保留期 + 双人复核 + 硬删）； 实际硬删由
 * {@code TenantDeletionReconciler} 异步续跑，confirmDeletion 只把租户迁入 DELETING 认领态。
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

    /** 归档租户（ACTIVE/SUSPENDED → ARCHIVED，打保留期起点戳；P3-T5，S-4=方案 A）。 */
    TenantVO archiveTenant(TenantArchiveCmd cmd);

    /**
     * 签发一次性删除审批令牌（双人复核第一步，项目负责人操作；P3-T5）。
     *
     * @return 含<b>仅此一次可见</b>的 raw token 的审批视图
     */
    TenantDeleteApprovalVO issueDeleteApproval(TenantDeleteApprovalCmd cmd);

    /**
     * 确认删除（双人复核第二步，第二操作者持 raw token；P3-T5）。
     *
     * <p>校验令牌有效 + 保留期已过 + 确认者 ≠ 签发者后，把 ARCHIVED 迁入 DELETING 认领态，硬删交由 reconciler 续跑。
     */
    TenantVO confirmDeletion(TenantDeleteCmd cmd);

    /** 删除预览（dry-run，不删；返回保留期状态与各表待删行数；P3-T5）。 */
    TenantDeletionPreviewVO previewDeletion(String tenantKey);

    /** 按业务键查询单个租户。 */
    TenantVO getTenant(String tenantKey);

    /** 列出全部租户。 */
    List<TenantVO> listTenants();
}
