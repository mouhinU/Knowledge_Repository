package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.executor.platform.*;
import com.mouhin.knowledge.repository.client.api.PlatformTenantServiceI;
import com.mouhin.knowledge.repository.client.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;

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
    private final ReissueInviteCmdExe reissueInviteCmdExe;
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
            ReissueInviteCmdExe reissueInviteCmdExe,
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
        this.reissueInviteCmdExe = reissueInviteCmdExe;
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
    public TenantReissueInviteVO reissueInvite(TenantReissueInviteCmd cmd) {
        return reissueInviteCmdExe.execute(cmd);
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
