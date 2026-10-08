package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.executor.platform.TenantCreateCmdExe;
import com.mouhin.knowledge.repository.application.executor.platform.TenantGetQryExe;
import com.mouhin.knowledge.repository.application.executor.platform.TenantListQryExe;
import com.mouhin.knowledge.repository.application.executor.platform.TenantResumeCmdExe;
import com.mouhin.knowledge.repository.application.executor.platform.TenantSuspendCmdExe;
import com.mouhin.knowledge.repository.client.api.PlatformTenantServiceI;
import com.mouhin.knowledge.repository.client.dto.TenantCreateCmd;
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
    private final TenantSuspendCmdExe tenantSuspendCmdExe;
    private final TenantResumeCmdExe tenantResumeCmdExe;
    private final TenantGetQryExe tenantGetQryExe;
    private final TenantListQryExe tenantListQryExe;

    public PlatformTenantServiceImpl(
            TenantCreateCmdExe tenantCreateCmdExe,
            TenantSuspendCmdExe tenantSuspendCmdExe,
            TenantResumeCmdExe tenantResumeCmdExe,
            TenantGetQryExe tenantGetQryExe,
            TenantListQryExe tenantListQryExe) {
        this.tenantCreateCmdExe = tenantCreateCmdExe;
        this.tenantSuspendCmdExe = tenantSuspendCmdExe;
        this.tenantResumeCmdExe = tenantResumeCmdExe;
        this.tenantGetQryExe = tenantGetQryExe;
        this.tenantListQryExe = tenantListQryExe;
    }

    @Override
    public TenantVO createTenant(TenantCreateCmd cmd) {
        return tenantCreateCmdExe.execute(cmd);
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
    public TenantVO getTenant(String tenantKey) {
        return tenantGetQryExe.execute(tenantKey);
    }

    @Override
    public List<TenantVO> listTenants() {
        return tenantListQryExe.execute();
    }
}
