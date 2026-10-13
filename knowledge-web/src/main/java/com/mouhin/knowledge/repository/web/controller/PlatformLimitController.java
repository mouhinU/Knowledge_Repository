package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.client.dto.TenantPlanLimitVO;
import com.mouhin.knowledge.repository.domain.gateway.TenantPlanLimitGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.QuotaPolicy;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantPlanLimit;
import com.mouhin.knowledge.repository.domain.model.valueobject.UsageMetric;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 平台端套餐限额管理控制器（adapter 层，P6-T2）。
 *
 * <p>挂载 {@code /api/platform/limits}，提供套餐限额的 CRUD 端点。 仅当 {@code knowledge.platform.enabled=true}
 * 才注册本控制器。
 *
 * @author mouhinU
 * @date 2026-10-12
 */
@RestController
@RequestMapping("/api/platform/limits")
@ConditionalOnProperty(name = "knowledge.platform.enabled", havingValue = "true")
@Slf4j
public class PlatformLimitController {

    private static final String FIELD_TENANT_KEY = "tenantKey";
    private static final String FIELD_METRIC = "metric";
    private static final String FIELD_QUOTA = "quota";
    private static final String FIELD_POLICY = "policy";

    private final TenantPlanLimitGateway tenantPlanLimitGateway;

    public PlatformLimitController(TenantPlanLimitGateway tenantPlanLimitGateway) {
        this.tenantPlanLimitGateway = tenantPlanLimitGateway;
    }

    private static TenantPlanLimitVO toVO(String tenantKey, TenantPlanLimit limit) {
        TenantPlanLimitVO vo = new TenantPlanLimitVO();
        vo.setTenantKey(tenantKey);
        vo.setMetric(limit.metric().code());
        vo.setQuota(limit.quota());
        vo.setPolicy(limit.policy().name());
        return vo;
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 防御性解析整型/长整型请求体字段：非数值返回 {@code null}。 */
    private static Long longOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 查询指定租户的全部限额配置。 */
    @GetMapping
    public ResponseEntity<List<TenantPlanLimitVO>> list(@RequestParam("tenant") String tenantKey) {
        List<TenantPlanLimit> limits = tenantPlanLimitGateway.findByTenantKey(tenantKey);
        List<TenantPlanLimitVO> result =
                limits.stream().map(limit -> toVO(tenantKey, limit)).toList();
        return ResponseEntity.ok(result);
    }

    /** 新增或更新限额（upsert 语义）。 */
    @PostMapping
    public ResponseEntity<TenantPlanLimitVO> create(@RequestBody Map<String, Object> body) {
        String tenantKey = stringOf(body.get(FIELD_TENANT_KEY));
        String metricCode = stringOf(body.get(FIELD_METRIC));
        Long quota = longOf(body.get(FIELD_QUOTA));
        String policyStr = stringOf(body.get(FIELD_POLICY));

        UsageMetric metric = UsageMetric.fromCode(metricCode);
        QuotaPolicy policy = QuotaPolicy.valueOf(policyStr);
        TenantPlanLimit limit = new TenantPlanLimit(metric, quota, policy);
        tenantPlanLimitGateway.saveOrUpdate(tenantKey, limit);

        return ResponseEntity.ok(toVO(tenantKey, limit));
    }

    /** 删除指定租户指定 metric 的限额。 */
    @DeleteMapping("/{tenantKey}/{metric}")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable String tenantKey, @PathVariable String metric) {
        UsageMetric usageMetric = UsageMetric.fromCode(metric);
        tenantPlanLimitGateway.delete(tenantKey, usageMetric);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", "deleted");
        body.put("tenantKey", tenantKey);
        body.put("metric", metric);
        return ResponseEntity.ok(body);
    }
}
