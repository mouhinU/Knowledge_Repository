package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.client.api.PlatformTenantServiceI;
import com.mouhin.knowledge.repository.client.dto.TenantArchiveCmd;
import com.mouhin.knowledge.repository.client.dto.TenantCreateCmd;
import com.mouhin.knowledge.repository.client.dto.TenantDeleteApprovalCmd;
import com.mouhin.knowledge.repository.client.dto.TenantDeleteApprovalVO;
import com.mouhin.knowledge.repository.client.dto.TenantDeleteCmd;
import com.mouhin.knowledge.repository.client.dto.TenantDeletionPreviewVO;
import com.mouhin.knowledge.repository.client.dto.TenantProvisionCmd;
import com.mouhin.knowledge.repository.client.dto.TenantProvisionVO;
import com.mouhin.knowledge.repository.client.dto.TenantReissueInviteCmd;
import com.mouhin.knowledge.repository.client.dto.TenantReissueInviteVO;
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
 * <p>本期交付 create / suspend / resume / get / list 五端点；P3-T5 叠加 archive / deletion-approval / delete
 * / deletion-preview 四端点（S-4=方案 A：归档 + 30 天保留期 + 双人复核 + 硬删，硬删由 reconciler 异步续跑）； P3-T7
 * 邀请式设密<b>回访</b>子切片叠加 {@code POST /{tenantKey}/invites/reissue}：运维代位重签 首位 admin 邀请（旧 PENDING 全部吊销 +
 * 落新邀请 + 一次性可见 raw code）。
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
    private static final String FIELD_APPROVAL_TOKEN = "approvalToken";
    private static final String FIELD_TTL_HOURS = "ttlHours";
    private static final String FIELD_USERNAME = "username";

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

    @PostMapping("/{tenantKey}/archive")
    public ResponseEntity<TenantVO> archive(
            @PathVariable String tenantKey,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        TenantArchiveCmd cmd = new TenantArchiveCmd();
        cmd.setTenantKey(tenantKey);
        cmd.setReason(body == null ? null : stringOf(body.get(FIELD_REASON)));
        applyOperator(request, cmd::setOperatorId, cmd::setOperatorName);
        return ResponseEntity.ok(platformTenantService.archiveTenant(cmd));
    }

    @PostMapping("/{tenantKey}/deletion-approval")
    public ResponseEntity<TenantDeleteApprovalVO> issueDeletionApproval(
            @PathVariable String tenantKey,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        TenantDeleteApprovalCmd cmd = new TenantDeleteApprovalCmd();
        cmd.setTenantKey(tenantKey);
        cmd.setTtlHours(body == null ? null : intOf(body.get(FIELD_TTL_HOURS)));
        applyOperator(request, cmd::setOperatorId, cmd::setOperatorName);
        return ResponseEntity.ok(platformTenantService.issueDeleteApproval(cmd));
    }

    @PostMapping("/{tenantKey}/delete")
    public ResponseEntity<TenantVO> delete(
            @PathVariable String tenantKey,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        TenantDeleteCmd cmd = new TenantDeleteCmd();
        cmd.setTenantKey(tenantKey);
        cmd.setApprovalToken(body == null ? null : stringOf(body.get(FIELD_APPROVAL_TOKEN)));
        applyOperator(request, cmd::setOperatorId, cmd::setOperatorName);
        return ResponseEntity.ok(platformTenantService.confirmDeletion(cmd));
    }

    /**
     * 重签首位 admin 邀请（P3-T7 邀请式设密<b>回访</b>）：由平台运维在旧邀请丢失 / 过期 / 已吊销时手动触发。
     *
     * <p>请求体：{@code {"username": "...", "ttlHours": 72}}（{@code ttlHours} 可缺省，服务侧回落到 {@code
     * UserInviteService.DEFAULT_TTL}）。响应体：{@link TenantReissueInviteVO} 含新 raw code、落地
     * URL、失效时间与本次吊销的旧 PENDING 数量——{@code code} 仅此一次可见，运维须通过安全渠道转交租户管理员。
     *
     * <p>语义：定位 (tenantKey, username) → {@code UserInviteService.issue} 内建"同用户 PENDING 全吊销 +
     * 落新"原子语义。
     */
    @PostMapping("/{tenantKey}/invites/reissue")
    public ResponseEntity<TenantReissueInviteVO> reissueInvite(
            @PathVariable String tenantKey,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        TenantReissueInviteCmd cmd = new TenantReissueInviteCmd();
        cmd.setTenantKey(tenantKey);
        cmd.setUsername(body == null ? null : stringOf(body.get(FIELD_USERNAME)));
        cmd.setTtlHours(body == null ? null : intOf(body.get(FIELD_TTL_HOURS)));
        applyOperator(request, cmd::setOperatorId, cmd::setOperatorName);
        return ResponseEntity.ok(platformTenantService.reissueInvite(cmd));
    }

    @GetMapping("/{tenantKey}/deletion-preview")
    public ResponseEntity<TenantDeletionPreviewVO> deletionPreview(@PathVariable String tenantKey) {
        return ResponseEntity.ok(platformTenantService.previewDeletion(tenantKey));
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

    /** 防御性解析整型请求体字段：非数值 / 缺省返回 {@code null}，由服务层回落默认值（不因脏输入 400）。 */
    private static Integer intOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
