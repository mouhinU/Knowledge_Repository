package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.client.api.PlatformTenantServiceI;
import com.mouhin.knowledge.repository.client.dto.TenantCreateCmd;
import com.mouhin.knowledge.repository.client.dto.TenantProvisionCmd;
import com.mouhin.knowledge.repository.client.dto.TenantProvisionVO;
import com.mouhin.knowledge.repository.client.dto.TenantStatusCmd;
import com.mouhin.knowledge.repository.client.dto.TenantVO;
import com.mouhin.knowledge.repository.domain.model.valueobject.PlatformTokenPayload;
import com.mouhin.knowledge.repository.web.security.PlatformTokenAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台端租户生命周期控制器（adapter 层，P3-T1 / T-4 方案 B）。
 *
 * <p>挂载 {@code /api/platform/tenants/**}，由独立 {@link PlatformTokenAuthFilter} 保护（平台令牌，与租户管理员令牌隔离）。
 * 仅负责请求适配：解析入参组装 Command、把过滤器落下的 {@link PlatformTokenPayload} 操作者身份注入命令（供审计溯源）， 调用 app 层 {@link
 * PlatformTenantServiceI}，返回 VO 作为响应体。
 *
 * <p><b>门控</b>：仅当 {@code knowledge.platform.enabled=true} 才注册本控制器（{@link ConditionalOnProperty}），
 * 默认关闭态下 {@code /api/platform/**} 整体不可达。
 *
 * <p>本期交付 create / suspend / resume / get / list 五端点；archive / delete 完整编排属 P3-T5，暂不暴露。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@RestController
@RequestMapping("/api/platform/tenants")
@ConditionalOnProperty(name = "knowledge.platform.enabled", havingValue = "true")
@Slf4j
public class PlatformTenantController {

    /** 请求体字段名（抽常量防多处漂移，java:S1192）。 */
    private static final String FIELD_TENANT_KEY = "tenantKey";

    private static final String FIELD_NAME = "name";
    private static final String FIELD_PLAN = "plan";
    private static final String FIELD_DATA_REGION = "dataRegion";
    private static final String FIELD_REASON = "reason";
    private static final String FIELD_PROVISION_REQUEST_ID = "provisionRequestId";

    private final PlatformTenantServiceI platformTenantService;

    public PlatformTenantController(PlatformTenantServiceI platformTenantService) {
        this.platformTenantService = platformTenantService;
    }

    @GetMapping
    public ResponseEntity<List<TenantVO>> list() {
        return ResponseEntity.ok(platformTenantService.listTenants());
    }

    @GetMapping("/{tenantKey}")
    public ResponseEntity<TenantVO> get(@PathVariable String tenantKey) {
        return ResponseEntity.ok(platformTenantService.getTenant(tenantKey));
    }

    @PostMapping
    public ResponseEntity<TenantVO> create(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        TenantCreateCmd cmd = new TenantCreateCmd();
        cmd.setTenantKey(stringOf(body.get(FIELD_TENANT_KEY)));
        cmd.setName(stringOf(body.get(FIELD_NAME)));
        cmd.setPlan(stringOf(body.get(FIELD_PLAN)));
        cmd.setDataRegion(stringOf(body.get(FIELD_DATA_REGION)));
        applyOperator(request, cmd::setOperatorId, cmd::setOperatorName);
        return ResponseEntity.ok(platformTenantService.createTenant(cmd));
    }

    @PostMapping("/{tenantKey}/provision")
    public ResponseEntity<TenantProvisionVO> provision(
            @PathVariable String tenantKey,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        TenantProvisionCmd cmd = new TenantProvisionCmd();
        cmd.setTenantKey(tenantKey);
        cmd.setProvisionRequestId(
                body == null ? null : stringOf(body.get(FIELD_PROVISION_REQUEST_ID)));
        applyOperator(request, cmd::setOperatorId, cmd::setOperatorName);
        return ResponseEntity.ok(platformTenantService.provisionTenant(cmd));
    }

    @PostMapping("/{tenantKey}/suspend")
    public ResponseEntity<TenantVO> suspend(
            @PathVariable String tenantKey,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                platformTenantService.suspendTenant(statusCmd(tenantKey, body, request)));
    }

    @PostMapping("/{tenantKey}/resume")
    public ResponseEntity<TenantVO> resume(
            @PathVariable String tenantKey,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                platformTenantService.resumeTenant(statusCmd(tenantKey, body, request)));
    }

    private TenantStatusCmd statusCmd(
            String tenantKey, Map<String, Object> body, HttpServletRequest request) {
        TenantStatusCmd cmd = new TenantStatusCmd();
        cmd.setTenantKey(tenantKey);
        cmd.setReason(body == null ? null : stringOf(body.get(FIELD_REASON)));
        applyOperator(request, cmd::setOperatorId, cmd::setOperatorName);
        return cmd;
    }

    /** 从过滤器落下的平台令牌载荷读取操作者身份，注入命令（缺载荷时留空，由审计侧记为未知）。 */
    private void applyOperator(
            HttpServletRequest request,
            java.util.function.Consumer<String> idSetter,
            java.util.function.Consumer<String> nameSetter) {
        Object attr = request.getAttribute(PlatformTokenAuthFilter.PRINCIPAL_ATTRIBUTE);
        if (attr instanceof PlatformTokenPayload payload) {
            idSetter.accept(payload.operatorId());
            nameSetter.accept(payload.operatorName());
        }
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
