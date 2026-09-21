package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.executor.system.SystemStatusQryExe;
import com.mouhin.knowledge.repository.client.api.SystemConfigServiceI;
import com.mouhin.knowledge.repository.client.dto.SystemStatusVO;
import org.springframework.stereotype.Service;

/**
 * 系统配置应用服务实现（app 层，仅分发到 Executor）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Service
public class SystemConfigServiceImpl implements SystemConfigServiceI {

    private final SystemStatusQryExe systemStatusQryExe;

    public SystemConfigServiceImpl(SystemStatusQryExe systemStatusQryExe) {
        this.systemStatusQryExe = systemStatusQryExe;
    }

    @Override
    public SystemStatusVO getSystemStatus() {
        return systemStatusQryExe.execute();
    }
}
