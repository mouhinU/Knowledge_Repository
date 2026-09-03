package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.service.SystemConfigApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统配置控制器
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@RestController
@RequestMapping("/api/admin/system")
public class SystemConfigController {

    private final SystemConfigApplicationService systemConfigService;

    public SystemConfigController(SystemConfigApplicationService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /**
     * 获取系统状态概览（Embedding 模型、Milvus 连接、知识库统计）
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(systemConfigService.getSystemStatus());
    }
}
