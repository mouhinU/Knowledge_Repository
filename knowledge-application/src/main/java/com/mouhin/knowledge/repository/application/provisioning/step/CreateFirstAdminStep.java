package com.mouhin.knowledge.repository.application.provisioning.step;

import com.mouhin.knowledge.repository.application.service.ProvisioningOutcome.InviteInfo;
import com.mouhin.knowledge.repository.application.support.UserInviteService;
import com.mouhin.knowledge.repository.application.support.UserInviteService.InviteIssueResult;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.domain.provisioning.ProvisioningContext;
import com.mouhin.knowledge.repository.domain.provisioning.ProvisioningStep;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 建租户首位 admin 步骤（P3-T3，order=400，最后一步）。
 *
 * <p>为新租户建 {@code is_admin=TRUE / status=ACTIVE / must_change_password=1 / password_hash=NULL}
 * 的首位管理员， 并签发一条 72h TTL 的一次性设密邀请（{@link UserInviteService#issue}）。raw code 通过 {@link
 * ProvisioningContext#attributes()} 键 {@link ProvisioningContext#ATTR_INVITE_INFO} 回传给状态机 → {@code
 * ProvisioningOutcome.InviteInfo} → {@code TenantProvisionVO}，仅在 provision HTTP 响应体一次性出现。
 *
 * <p><b>username 命名</b>：{@code {tenantKey}-admin}（如 {@code acme-admin}）。命名空间化避开与 V15 种子的 default 租户
 * {@code admin} 冲突；{@code sys_user.username} 现无 DB 唯一约束，租户内唯一性由 {@link
 * UserGateway#findByUsernameAndTenant} 前置查重保证。
 *
 * <p><b>password_hash=NULL 而非哨兵值</b>：{@code AdminLoginCmdExe} 用 {@code filter(u ->
 * u.getPasswordHash() != null && !u.getPasswordHash().isBlank())} 阻断空 hash 登录；因此 null
 * 是"不可密码登录"的既有语义，无需引入新的哨兵字符串。
 *
 * <p><b>幂等（两层）</b>：
 *
 * <ul>
 *   <li><b>资源级</b>（本步骤 {@code execute} 内部）：先 {@code findByUsernameAndTenant} 命中即复用既有 user（不重复创建）；
 *       只要 {@code execute} 真正运行，就重签一条新邀请（{@link UserInviteService#issue} 内部会先吊销同用户旧 PENDING 邀请）。
 *   <li><b>步骤级</b>（由 {@code ProvisioningStateMachine} 决定）：若本步骤对同一 {@code (tenantId, requestId)} 已记
 *       SUCCESS， 重放时被 SKIPPED、{@code execute} 不再运行，故<b>不</b>重签邀请——重放一次已完成的开通是无副作用的 no-op。
 *       只有"provisioning 中断/失败且本步骤未达 SUCCESS"的重放才会重新 {@code execute} 并给出新码。
 * </ul>
 *
 * <p>因此"运维丢失已完成开通的邀请码"不能靠重放 provision 找回，须走独立的邀请重签路径（后续任务），本步骤不承担该职责。
 *
 * <p><b>不</b>发引导 JWT：清单 C-3 决策——管理员凭 raw code 走 {@code POST /api/admin/auth/set-password-by-invite}
 * 设密后即可用标准 {@code POST /api/admin/auth/login} 换正式 admin token，避免"响应体回显长期令牌"红线。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Component
public class CreateFirstAdminStep implements ProvisioningStep {

    private static final Logger log = LoggerFactory.getLogger(CreateFirstAdminStep.class);

    /** 步骤名常量。 */
    public static final String STEP_NAME = "CREATE_FIRST_ADMIN";

    /** 排序权重：所有开通步骤最后执行（依赖前序 sys_config / 根部门已就位）。 */
    public static final int STEP_ORDER = 400;

    /** 首位 admin username 后缀，与 tenantKey 拼接：{@code acme + "-admin" = "acme-admin"}。 */
    public static final String ADMIN_USERNAME_SUFFIX = "-admin";

    /** 邀请 URL 前缀（相对路径，前端 host 由 caller 决定）。 */
    public static final String INVITE_URL_PREFIX = "/set-password.html?code=";

    /** 邀请 TTL；与 {@link UserInviteService#DEFAULT_TTL} 对齐。 */
    private static final Duration INVITE_TTL = UserInviteService.DEFAULT_TTL;

    private final UserGateway userGateway;
    private final UserInviteService userInviteService;

    public CreateFirstAdminStep(UserGateway userGateway, UserInviteService userInviteService) {
        this.userGateway = userGateway;
        this.userInviteService = userInviteService;
    }

    @Override
    public String name() {
        return STEP_NAME;
    }

    @Override
    public int order() {
        return STEP_ORDER;
    }

    @Override
    public void execute(ProvisioningContext context) {
        Long tenantId = context.tenantId();
        String tenantKey = context.tenantKey();
        if (tenantId == null || tenantKey == null || tenantKey.isBlank()) {
            throw new IllegalStateException("Tenant id/key required to create first admin");
        }
        String username = tenantKey + ADMIN_USERNAME_SUFFIX;

        User admin = findOrCreateAdmin(tenantId, username);
        if (admin.getId() == null) {
            throw new IllegalStateException(
                    "Admin user id was not populated after saveWithTenant (username="
                            + username
                            + ")");
        }

        // 无论 user 是否复用，都重签一条新邀请：旧邀请会被 UserInviteService.issue 内部吊销
        InviteIssueResult result = userInviteService.issue(tenantId, admin.getId(), INVITE_TTL);

        InviteInfo info =
                new InviteInfo(
                        result.rawCode(),
                        INVITE_URL_PREFIX + result.rawCode(),
                        result.invite().getExpiresAt(),
                        username);
        context.attributes().put(ProvisioningContext.ATTR_INVITE_INFO, info);

        log.info(
                "[Provisioning] CreateFirstAdmin done: tenant={} username={} userId={} inviteId={}",
                tenantKey,
                username,
                admin.getId(),
                result.invite().getInviteId());
    }

    /** 命中即复用，未命中则新建（首位 admin 幂等语义）。 */
    private User findOrCreateAdmin(Long tenantId, String username) {
        Optional<User> existing = userGateway.findByUsernameAndTenant(username, tenantId);
        if (existing.isPresent()) {
            log.info(
                    "[Provisioning] CreateFirstAdmin reusing existing admin user: tenantId={} username={} userId={}",
                    tenantId,
                    username,
                    existing.get().getId());
            return existing.get();
        }
        User admin = new User();
        admin.setUserKey(UUID.randomUUID().toString());
        admin.setUsername(username);
        admin.setAdmin(Boolean.TRUE);
        admin.setStatus(User.STATUS_ACTIVE);
        admin.setMustChangePassword(Boolean.TRUE);
        // password_hash 刻意留 null：AdminLoginCmdExe 会过滤掉空 hash 账号，阻断"未设密即登录"路径
        admin.setPasswordHash(null);
        userGateway.saveWithTenant(admin, tenantId);
        return admin;
    }
}
