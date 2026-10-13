package com.mouhin.knowledge.repository.application.executor.system;

import com.mouhin.knowledge.repository.application.support.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 系统配置更新执行器
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
@Slf4j
public class UpdateSystemConfigCmdExe {

    private final SystemConfigService systemConfigService;

    public UpdateSystemConfigCmdExe(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /**
     * 更新单个配置值
     *
     * @param configKey 配置键
     * @param configValue 新值
     */
    public void execute(String configKey, String configValue) {
        systemConfigService.updateValue(configKey, configValue);
    }
}
