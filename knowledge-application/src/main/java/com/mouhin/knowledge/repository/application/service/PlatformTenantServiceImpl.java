package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.executor.platform.ArchiveTenantCmdExe;
import com.mouhin.knowledge.repository.application.executor.platform.ConfirmTenantDeletionCmdExe;
import com.mouhin.knowledge.repository.application.executor.platform.IssueDeleteApprovalCmdExe;
import com.mouhin.knowledge.repository.application.executor.platform.TenantCreateCmdExe;
import com.mouhin.knowledge.repository.application.executor.platform.TenantDeletionPreviewQryExe;
import com.mouhin.knowledge.repository.application.executor.platform.TenantGetQryExe;
import com.mouhin.knowledge.repository.application.executor.platform.TenantListQryExe;
import com.mouhin.knowledge.repository.application.executor.platform.TenantProvisionCmdExe;
import com.mouhin.knowledge.repository.application.executor.platform.TenantResumeCmdExe;
import com.mouhin.knowledge.repository.application.executor.platform.TenantSuspendCmdExe;
import com.mouhin.knowledge.repository.client.api.PlatformTenantServiceI;
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
import org.springframework.stereotype.Service;

/**
 * 平台端租户生命周期应用服务实现（app 层，仅分发到 Executor，COLA 红线 #8，P3-T1）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Service
public class PlatformTenantServiceImpl implements PlatformTenantServiceI {

    private final TenantCreateCmdExe tenantCreateCmdExe;
    private final TenantProvisionCmdExe tenantProvisionCmdExe;
    private final TenantSuspendCmdExe tenantSuspendCmdExe;
    private final TenantResumeCmdExe tenantResumeCmdExe;
    private final ArchiveTenantCmdExe archiveTenantCmdExe;
    private final IssueDeleteApprovalCmdExe issueDeleteApprovalCmdExe;
    private final ConfirmTenantDeletionCmdExe confirmTenantDeletionCmdExe;
    private final TenantDeletionPreviewQryExe tenantDeletionPreviewQryExe;
    private final TenantGetQryExe tenantGetQryExe;
    private final TenantListQryExe tenantListQryExe;

    public PlatformTenantServiceImpl(
            TenantCreateCmdExe tenantCreateCmdExe,
            TenantProvisionCmdExe tenantProvisionCmdExe,
            TenantSuspendCmdExe tenantSuspendCmdExe,
            TenantResumeCmdExe tenantResumeCmdExe,
            ArchiveTenantCmdExe archiveTenantCmdExe,
            IssueDeleteApprovalCmdExe issueDeleteApprovalCmdExe,
            ConfirmTenantDeletionCmdExe confirmTenantDeletionCmdExe,
            TenantDeletionPreviewQryExe tenantDeletionPreviewQryExe,
            TenantGetQryExe tenantGetQryExe,
            TenantListQryExe tenantListQryExe) {
        this.tenantCreateCmdExe = tenantCreateCmdExe;
        this.tenantProvisionCmdExe = tenantProvisionCmdExe;
        this.tenantSuspendCmdExe = tenantSuspendCmdExe;
        this.tenantResumeCmdExe = tenantResumeCmdExe;
        this.archiveTenantCmdExe = archiveTenantCmdExe;
        this.issueDeleteApprovalCmdExe = issueDeleteApprovalCmdExe;
        this.confirmTenantDeletionCmdExe = confirmTenantDeletionCmdExe;
        this.tenantDeletionPreviewQryExe = tenantDeletionPreviewQryExe;
        this.tenantGetQryExe = tenantGetQryExe;
        this.tenantListQryExe = tenantListQryExe;
    }

    @Override
    public TenantVO createTenant(TenantCreateCmd cmd) {
        return tenantCreateCmdExe.execute(cmd);
    }

    @Override
    public TenantProvisionVO provisionTenant(TenantProvisionCmd cmd) {
        return tenantProvisionCmdExe.execute(cmd);
    }

    @Override
    public TenantVO suspendTenant(TenantStatusCmd cmd) {
        return tenantSuspendCmdExe.execute(cmd);
    }

    @Override
    public TenantVO resumeTenant(TenantStatusCmd cmd) {
        return tenantResumeCmdExe.execute(cmd);
    }

    @Override
    public TenantVO archiveTenant(TenantArchiveCmd cmd) {
        return archiveTenantCmdExe.execute(cmd);
    }

    @Override
    public TenantDeleteApprovalVO issueDeleteApproval(TenantDeleteApprovalCmd cmd) {
        return issueDeleteApprovalCmdExe.execute(cmd);
    }

    @Override
    public TenantVO confirmDeletion(TenantDeleteCmd cmd) {
        return confirmTenantDeletionCmdExe.execute(cmd);
    }

    @Override
    public TenantDeletionPreviewVO previewDeletion(String tenantKey) {
        return tenantDeletionPreviewQryExe.execute(tenantKey);
    }

    @Override
    public TenantVO getTenant(String tenantKey) {
        return tenantGetQryExe.execute(tenantKey);
    }

    @Override
    public List<TenantVO> listTenants() {
        return tenantListQryExe.execute();
    }
}
