package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.client.api.SystemConfigServiceI;
import com.mouhin.knowledge.repository.client.dto.SystemConfigDTO;
import com.mouhin.knowledge.repository.client.dto.SystemStatusVO;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统配置控制器（adapter 层）
 *
 * @author mouhinU
 * @date 2026-09-02
 */
@RestController
@RequestMapping("/api/admin/system")
public class SystemConfigController {

    private final SystemConfigServiceI systemConfigService;

    public SystemConfigController(SystemConfigServiceI systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /** 获取系统状态概览（Embedding 模型、Milvus 连接、知识库统计） */
    @GetMapping("/status")
    public ResponseEntity<SystemStatusVO> getStatus() {
        return ResponseEntity.ok(systemConfigService.getSystemStatus());
    }

    /** 查询全部系统配置（特性开关 + 运行时参数） */
    @GetMapping("/configs")
    public ResponseEntity<List<SystemConfigDTO>> listConfigs() {
        return ResponseEntity.ok(systemConfigService.listConfigs());
    }

    /** 更新单个配置值 */
    @PutMapping("/configs")
    public ResponseEntity<Void> updateConfig(@RequestBody Map<String, String> body) {
        String configKey = body.get("configKey");
        String configValue = body.get("configValue");
        if (configKey == null || configKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        systemConfigService.updateConfig(configKey, configValue != null ? configValue : "");
        return ResponseEntity.ok().build();
    }
}
