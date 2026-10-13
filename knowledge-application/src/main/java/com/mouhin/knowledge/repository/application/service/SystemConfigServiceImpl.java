package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.executor.system.InvalidateConfigCacheCmdExe;
import com.mouhin.knowledge.repository.application.executor.system.ListSystemConfigsQryExe;
import com.mouhin.knowledge.repository.application.executor.system.SystemStatusQryExe;
import com.mouhin.knowledge.repository.application.executor.system.UpdateSystemConfigCmdExe;
import com.mouhin.knowledge.repository.client.api.SystemConfigServiceI;
import com.mouhin.knowledge.repository.client.dto.SystemConfigDTO;
import com.mouhin.knowledge.repository.client.dto.SystemStatusVO;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 系统配置应用服务实现（app 层，仅分发到 Executor）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Service
public class SystemConfigServiceImpl implements SystemConfigServiceI {

    private final SystemStatusQryExe systemStatusQryExe;
    private final ListSystemConfigsQryExe listSystemConfigsQryExe;
    private final UpdateSystemConfigCmdExe updateSystemConfigCmdExe;
    private final InvalidateConfigCacheCmdExe invalidateConfigCacheCmdExe;

    public SystemConfigServiceImpl(
            SystemStatusQryExe systemStatusQryExe,
            ListSystemConfigsQryExe listSystemConfigsQryExe,
            UpdateSystemConfigCmdExe updateSystemConfigCmdExe,
            InvalidateConfigCacheCmdExe invalidateConfigCacheCmdExe) {
        this.systemStatusQryExe = systemStatusQryExe;
        this.listSystemConfigsQryExe = listSystemConfigsQryExe;
        this.updateSystemConfigCmdExe = updateSystemConfigCmdExe;
        this.invalidateConfigCacheCmdExe = invalidateConfigCacheCmdExe;
    }

    @Override
    public SystemStatusVO getSystemStatus() {
        return systemStatusQryExe.execute();
    }

    @Override
    public List<SystemConfigDTO> listConfigs() {
        return listSystemConfigsQryExe.execute();
    }

    @Override
    public void updateConfig(String configKey, String configValue) {
        updateSystemConfigCmdExe.execute(configKey, configValue);
    }

    @Override
    public void invalidateTenantConfig(String tenantKey) {
        invalidateConfigCacheCmdExe.execute(tenantKey);
    }
}
