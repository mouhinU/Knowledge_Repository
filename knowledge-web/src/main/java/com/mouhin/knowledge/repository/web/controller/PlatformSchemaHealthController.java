package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.service.TenantSchemaRunner;
import com.mouhin.knowledge.repository.application.service.TenantSchemaRunner.SchemaCheckResult;
import com.mouhin.knowledge.repository.domain.constant.FeatureFlagKeys;
import com.mouhin.knowledge.repository.domain.model.valueobject.PlatformTokenPayload;
import com.mouhin.knowledge.repository.web.security.PlatformTokenAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台端 SILO schema 健康体检控制器（adapter 层，P3-T6）。
 *
 * <p>挂载 {@code /api/platform/schema-health}，由 {@link PlatformTokenAuthFilter} 保护。提供两个端点：
 *
 * <ul>
 *   <li>{@code GET /api/platform/schema-health}：只读体检，返回各 SILO 集合 schema 状态（不触发重新检查，直接调 runner
 *       做一次检查）
 *   <li>{@code POST /api/platform/schema-health/check}：手工触发一次完整 schema 对齐巡检
 * </ul>
 *
 * <p><b>门控</b>：仅当 {@code knowledge.platform.enabled=true} 才注册本控制器（{@link ConditionalOnProperty}）。
 *
 * @author QoderWork
 * @date 2026-10-12
 */
@RestController
@RequestMapping("/api/platform/schema-health")
@ConditionalOnProperty(name = FeatureFlagKeys.PLATFORM_ENABLED, havingValue = "true")
@Slf4j
public class PlatformSchemaHealthController {

    /** 无法解析平台操作者身份时的兜底名称。 */
    private static final String OPERATOR_UNKNOWN = "platform-unknown";

    private final TenantSchemaRunner schemaRunner;

    public PlatformSchemaHealthController(TenantSchemaRunner schemaRunner) {
        this.schemaRunner = schemaRunner;
    }

    /**
     * 只读体检：执行一次 SILO 集合 schema 检查并返回结果。
     *
     * <p>响应格式：
     *
     * <pre>{@code
     * {
     *   "status": "IDLE" | "HEALTHY" | "DEGRADED",
     *   "siloCount": 0,
     *   "aligned": 0,
     *   "misaligned": 0,
     *   "issues": [...]  // 仅 DEGRADED 时存在
     * }
     * }</pre>
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        SchemaCheckResult result = schemaRunner.manualCheck();
        return ResponseEntity.ok(result.toResponseMap());
    }

    /** 手工触发一次完整 schema 对齐巡检（与 GET 等价，但审计动作名为 {@code SILO_SCHEMA_CHECK_MANUAL}）。 */
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> triggerCheck(HttpServletRequest request) {
        String operator = resolveOperator(request);
        log.info("[schema-health] manual check triggered by operator={}", operator);
        SchemaCheckResult result = schemaRunner.manualCheck();
        return ResponseEntity.ok(result.toResponseMap());
    }

    /** 从请求属性中提取平台令牌操作者（镜像 {@code PlatformTenantController} 模式）。 */
    private String resolveOperator(HttpServletRequest request) {
        Object payload = request.getAttribute(PlatformTokenAuthFilter.PRINCIPAL_ATTRIBUTE);
        if (payload instanceof PlatformTokenPayload p) {
            return p.operatorName() != null ? p.operatorName() : OPERATOR_UNKNOWN;
        }
        return OPERATOR_UNKNOWN;
    }
}
