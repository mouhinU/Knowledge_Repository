package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.client.api.SystemConfigServiceI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置缓存手工失效控制器（P4-T2）。
 *
 * <p>提供 {@code POST /api/admin/config/invalidate?tenantKey=xxx} 端点，管理员可手工触发指定租户的
 * 配置缓存失效——清除本实例进程内缓存后通过 Redis pub/sub 广播到其他实例。
 *
 * <p>走 {@code AdminTokenAuthFilter} 鉴权（{@code /api/admin/**} 路径已被安全配置覆盖）。
 *
 * @author mouhinU
 * @date 2026-10-11
 */
@RestController
@RequestMapping("/api/admin/config")
public class ConfigInvalidateController {

    private final SystemConfigServiceI systemConfigService;

    public ConfigInvalidateController(SystemConfigServiceI systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /**
     * 手工失效指定租户的配置缓存（Redis pub/sub 广播 + 本实例即时清除）。
     */
    @PostMapping("/invalidate")
    public ResponseEntity<Void> invalidate(@RequestParam String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        systemConfigService.invalidateTenantConfig(tenantKey);
        return ResponseEntity.ok().build();
    }
}
