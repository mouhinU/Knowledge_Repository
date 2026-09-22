package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.client.api.SystemConfigServiceI;
import com.mouhin.knowledge.repository.client.dto.SystemStatusVO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
