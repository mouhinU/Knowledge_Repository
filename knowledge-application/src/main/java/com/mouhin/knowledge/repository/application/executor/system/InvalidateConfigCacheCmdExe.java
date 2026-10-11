package com.mouhin.knowledge.repository.application.executor.system;

import com.mouhin.knowledge.repository.application.support.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 手工失效租户配置缓存用例（P4-T2）。
 *
 * <p>由 {@code ConfigInvalidateController} 调用，清除本实例缓存后通过 Redis pub/sub 广播到其他实例。
 *
 * @author mouhinU
 * @date 2026-10-11
 */
@Component
@Slf4j
public class InvalidateConfigCacheCmdExe {

    private final SystemConfigService systemConfigService;

    public InvalidateConfigCacheCmdExe(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    public void execute(String tenantKey) {
        systemConfigService.invalidateTenant(tenantKey);
    }
}
