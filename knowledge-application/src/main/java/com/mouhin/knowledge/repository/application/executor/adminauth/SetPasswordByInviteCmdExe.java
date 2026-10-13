package com.mouhin.knowledge.repository.application.executor.adminauth;

import com.mouhin.knowledge.repository.application.support.UserInviteService;
import com.mouhin.knowledge.repository.client.dto.SetPasswordByInviteCmd;
import com.mouhin.knowledge.repository.client.dto.SetPasswordByInviteVO;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.gateway.UserInviteGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.domain.model.entity.UserInvite;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 邀请式设密命令执行器（P3-T3，app 层用例，事务边界）。
 *
 * <p>校验 raw invite code → 查库拿到 {@link UserInvite} → 校验 {@link UserInvite#isUsable} → 加载目标 {@link
 * User} → BCrypt 编码新密码 → 更新 user.passwordHash + mustChangePassword=false → 标记 invite USED → 审计。
 * 全流程一个事务：任一环节失败均回滚，避免出现"密码已改但邀请未消费"或反之的半成品状态。
 *
 * <p><b>不</b>签发任何令牌：设密完成后管理员须走标准 {@code POST /api/admin/auth/login} 换正式 admin token （清单 C-3 决策 / 提案
 * §10 R1：不发引导 token，走标准登录）。
 *
 * <p><b>安全纪律</b>（红线 #4）：{@code cmd.inviteCode} 与 {@code cmd.newPassword} 全链禁止 log； 只允许脱敏记录 {@code
 * inviteId} 与 {@code codeHash} 前 8 位。异常消息亦不得回显 raw code。
 *
 * <p><b>密码强度</b>：仅做最小长度校验（{@value #MIN_PASSWORD_LENGTH}–{@value #MAX_PASSWORD_LENGTH} 字符）；
 * 复杂度策略（大小写 / 数字 / 符号）留给前端 UI 提示与将来 P4 阶段的全局密码策略配置，避免在设密入口 引入不一致的强度门槛。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Component
@Slf4j
public class SetPasswordByInviteCmdExe {

    /**
     * 审计动作名。
     */
    public static final String ACTION = "SET_PASSWORD_BY_INVITE";

    /**
     * 新密码最小长度；与既有 {@code AdminChangePasswordCmdExe} 的"非空"相比略收紧，防误设 1-2 位口令。
     */
    public static final int MIN_PASSWORD_LENGTH = 8;

    /**
     * 新密码最大长度；BCrypt 对 >72 字节输入直接抛异常（非截断），故上限与 BCrypt 硬限对齐为 72。
     */
    public static final int MAX_PASSWORD_LENGTH = 72;

    /**
     * 日志脱敏：hash 前缀可见长度。
     */
    private static final int HASH_LOG_PREFIX = 8;

    private final UserInviteService userInviteService;
    private final UserInviteGateway userInviteGateway;
    private final UserGateway userGateway;
    private final BCryptPasswordEncoder passwordEncoder;
    private final PlatformAuditLog platformAuditLog;

    public SetPasswordByInviteCmdExe(
            UserInviteService userInviteService,
            UserInviteGateway userInviteGateway,
            UserGateway userGateway,
            BCryptPasswordEncoder passwordEncoder,
            PlatformAuditLog platformAuditLog) {
        this.userInviteService = userInviteService;
        this.userInviteGateway = userInviteGateway;
        this.userGateway = userGateway;
        this.passwordEncoder = passwordEncoder;
        this.platformAuditLog = platformAuditLog;
    }

    /**
     * 执行设密。
     *
     * @param cmd 命令（含 raw invite code 与新密码）
     * @return 结果 VO；仅回显 username / tenantKey / 提示消息，不含任何令牌
     * @throws IllegalArgumentException 入参缺失 / 邀请码无效 / 已过期 / 已使用 / 密码长度越界
     * @throws IllegalStateException    目标用户不存在（数据不一致）
     */
    @Transactional
    public SetPasswordByInviteVO execute(SetPasswordByInviteCmd cmd) {
        validate(cmd);

        UserInvite invite =
                userInviteService
                        .findByRawCode(cmd.getInviteCode())
                        .orElseThrow(
                                () -> {
                                    platformAuditLog.logTenantAction(
                                            "anonymous",
                                            ACTION,
                                            "-",
                                            "REJECTED",
                                            "invite code not recognized");
                                    return new IllegalArgumentException("邀请码无效");
                                });

        LocalDateTime now = LocalDateTime.now();
        if (!invite.isUsable(now)) {
            String reason = invite.isExpired(now) ? "expired" : "not-pending";
            platformAuditLog.logTenantAction(
                    "anonymous",
                    ACTION,
                    "-",
                    "REJECTED",
                    "invite unusable (" + reason + ") inviteId=" + invite.getInviteId());
            throw new IllegalArgumentException("邀请码已过期或已被使用");
        }

        User user =
                userGateway
                        .findById(invite.getUserId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Target user not found for invite "
                                                        + invite.getInviteId()));

        user.setPasswordHash(passwordEncoder.encode(cmd.getNewPassword()));
        user.setMustChangePassword(Boolean.FALSE);
        userGateway.update(user);

        invite.markUsed(now);
        userInviteGateway.update(invite);

        platformAuditLog.logTenantAction(
                user.getUserKey(),
                ACTION,
                user.getTenantKey(),
                "SUCCESS",
                "inviteId=" + invite.getInviteId() + " userId=" + user.getId());
        log.info(
                "[SetPasswordByInvite] success: tenantKey={} username={} userId={} inviteId={} codeHashPrefix={}",
                user.getTenantKey(),
                user.getUsername(),
                user.getId(),
                invite.getInviteId(),
                invite.getInviteCodeHash()
                        .substring(
                                0, Math.min(HASH_LOG_PREFIX, invite.getInviteCodeHash().length())));

        SetPasswordByInviteVO vo = new SetPasswordByInviteVO();
        vo.setSuccess(true);
        vo.setUsername(user.getUsername());
        vo.setTenantKey(user.getTenantKey());
        vo.setMessage("密码已设置，请使用新密码登录");
        return vo;
    }

    private void validate(SetPasswordByInviteCmd cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (cmd.getInviteCode() == null || cmd.getInviteCode().isBlank()) {
            throw new IllegalArgumentException("邀请码不能为空");
        }
        String pwd = cmd.getNewPassword();
        if (pwd == null || pwd.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("新密码长度至少 " + MIN_PASSWORD_LENGTH + " 位");
        }
        if (pwd.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("新密码长度不能超过 " + MAX_PASSWORD_LENGTH + " 位");
        }
    }
}
