package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.application.provisioning.step.CreateFirstAdminStep;
import com.mouhin.knowledge.repository.application.support.UserInviteService;
import com.mouhin.knowledge.repository.application.support.UserInviteService.InviteIssueResult;
import com.mouhin.knowledge.repository.client.dto.TenantReissueInviteCmd;
import com.mouhin.knowledge.repository.client.dto.TenantReissueInviteVO;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.gateway.UserInviteGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.domain.model.entity.UserInvite;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantKey;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台端邀请重签执行器（app 层用例，事务边界，P3-T7 邀请式设密<b>回访</b>）。
 *
 * <p>用于首位 admin 未在 72h TTL 内消费初始邀请时，平台运维主动触发重签：定位 (tenantKey, username) → 通过 {@link
 * UserInviteService#issue} 生成新 raw code（旧 PENDING 邀请由 issue 内部吊销） → 返回一次性可见的新链接给运维，运维再通过 安全渠道（加密邮件
 * / IM 私聊）转交租户管理员。
 *
 * <p><b>与 P3-T3 provision 邀请链的关系</b>：{@code CreateFirstAdminStep} 内部也调 {@link
 * UserInviteService#issue}， 语义相同（吊销旧 PENDING + 落新邀请 + 返 raw code），差别仅在于触发方与定位方式——provision
 * 是租户首次开通时由状态机自动签发， 本执行器是运维在租户已 ACTIVE 但邀请链接失效/未消费时的手动补救；两者共享同一份签发实现保证零漂移。
 *
 * <p><b>不做</b>：
 *
 * <ul>
 *   <li>不校验租户当前状态——即便 ACTIVE/SUSPENDED/ARCHIVED 都可以重签（运维场景需要，且不影响数据完整性；DELETING/DELETED 由 {@code
 *       TenantDataPurgeGatewayImpl} 硬删时级联清理邀请表）。
 *   <li>不校验"目标 admin 是否已设过密码"——重签只影响邀请表，不动 {@code sys_user.passwordHash}；已设过密码的用户重签后仍可
 *       走邀请设密覆盖（业务上允许，例如 运维场景需要给管理员二次强制改密入口时）。
 * </ul>
 *
 * <p><b>安全纪律</b>（红线 #4）：raw code 仅返回体可见，审计与日志只记 {@code inviteId} + {@code tenantKey} + {@code
 * username}；{@code operatorId} 从平台令牌 claim 注入，与 {@code tenantKey} 一起落 {@link PlatformAuditLog}。
 *
 * <p><b>幂等</b>：{@link UserInviteService#issue} 内部先吊销同 (tenantId,userId) 下所有 PENDING 邀请再签新，
 * 重复调用只留最新一条有效；本执行器不额外加 provision-request-id 类幂等键（重签本身语义就是"再发一份新链接"）。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Component
@Slf4j
public class ReissueInviteCmdExe {

    /** 审计动作名。 */
    public static final String ACTION = "TENANT_INVITE_REISSUE";

    private final TenantGateway tenantGateway;
    private final UserGateway userGateway;
    private final UserInviteGateway userInviteGateway;
    private final UserInviteService userInviteService;
    private final PlatformAuditLog platformAuditLog;

    public ReissueInviteCmdExe(
            TenantGateway tenantGateway,
            UserGateway userGateway,
            UserInviteGateway userInviteGateway,
            UserInviteService userInviteService,
            PlatformAuditLog platformAuditLog) {
        this.tenantGateway = tenantGateway;
        this.userGateway = userGateway;
        this.userInviteGateway = userInviteGateway;
        this.userInviteService = userInviteService;
        this.platformAuditLog = platformAuditLog;
    }

    /**
     * 执行重签。
     *
     * @param cmd 命令（含 tenantKey + username + 可选 ttlHours + operator）
     * @return 结果 VO；{@code code} 是新 raw invite code，仅本响应体一次性可见
     * @throws IllegalArgumentException 入参缺失 / 租户或用户不存在
     */
    @Transactional
    public TenantReissueInviteVO execute(TenantReissueInviteCmd cmd) {
        validate(cmd);

        String operator = cmd.getOperatorId();
        String tenantKey = TenantKey.normalize(cmd.getTenantKey());

        Tenant tenant =
                tenantGateway
                        .findByTenantKey(tenantKey)
                        .orElseThrow(
                                () -> {
                                    platformAuditLog.logTenantAction(
                                            operator,
                                            ACTION,
                                            tenantKey,
                                            "REJECTED",
                                            "tenant not found");
                                    return new IllegalArgumentException(
                                            "Tenant not found: " + tenantKey);
                                });

        String username = cmd.getUsername().trim();
        Optional<User> userOpt =
                userGateway.findByUsernameAndTenant(username, tenant.getTenantId());
        if (userOpt.isEmpty()) {
            platformAuditLog.logTenantAction(
                    operator,
                    ACTION,
                    tenantKey,
                    "REJECTED",
                    "user not found: username=" + username);
            throw new IllegalArgumentException(
                    "User not found in tenant " + tenantKey + ": " + username);
        }
        User admin = userOpt.get();

        // 记录 issue 前的 PENDING 数量（issue 内部会全部吊销），供运维核对"这次重签撤销了几条旧链接"
        List<UserInvite> pendingBefore =
                userInviteGateway.findPendingByUser(tenant.getTenantId(), admin.getId());
        int revokedPrevious = pendingBefore.size();

        Duration ttl =
                (cmd.getTtlHours() == null || cmd.getTtlHours() <= 0)
                        ? UserInviteService.DEFAULT_TTL
                        : Duration.ofHours(cmd.getTtlHours());
        InviteIssueResult issue = userInviteService.issue(tenant.getTenantId(), admin.getId(), ttl);

        String inviteUrl = CreateFirstAdminStep.INVITE_URL_PREFIX + issue.rawCode();

        platformAuditLog.logTenantAction(
                operator,
                ACTION,
                tenantKey,
                "SUCCESS",
                "inviteId="
                        + issue.invite().getInviteId()
                        + " userId="
                        + admin.getId()
                        + " username="
                        + username
                        + " ttlHours="
                        + ttl.toHours()
                        + " revokedPrevious="
                        + revokedPrevious);
        log.info(
                "[ReissueInvite] tenant={} username={} inviteId={} ttlHours={} revokedPrevious={}",
                tenantKey,
                username,
                issue.invite().getInviteId(),
                ttl.toHours(),
                revokedPrevious);

        TenantReissueInviteVO vo = new TenantReissueInviteVO();
        vo.setTenantKey(tenantKey);
        vo.setUsername(username);
        vo.setCode(issue.rawCode());
        vo.setUrl(inviteUrl);
        vo.setExpiresAt(issue.invite().getExpiresAt());
        vo.setRevokedPrevious(revokedPrevious);
        return vo;
    }

    private void validate(TenantReissueInviteCmd cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (cmd.getTenantKey() == null || cmd.getTenantKey().isBlank()) {
            throw new IllegalArgumentException("tenantKey 不能为空");
        }
        if (cmd.getUsername() == null || cmd.getUsername().isBlank()) {
            throw new IllegalArgumentException("username 不能为空");
        }
    }
}
