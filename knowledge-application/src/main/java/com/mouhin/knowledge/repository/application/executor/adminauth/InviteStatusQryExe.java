package com.mouhin.knowledge.repository.application.executor.adminauth;

import com.mouhin.knowledge.repository.application.support.UserInviteService;
import com.mouhin.knowledge.repository.client.dto.InviteStatusVO;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.domain.model.entity.UserInvite;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 邀请码状态查询执行器（app 层只读用例，P3-T7 邀请式设密<b>回访</b>）。
 *
 * <p>前端邀请落地页（{@code /set-password.html?code=XXX}）加载时先调用本查询判定 raw code 的当前状态， 以便在提交新密码前就区分 "码错 / 已使用
 * / 已过期 / 已吊销"四态并给出对应引导文案。
 *
 * <p><b>安全边界</b>（红线 #4 + 反枚举）：
 *
 * <ul>
 *   <li>{@code code} 未命中任何 hash 行时返 {@link InviteStatusVO#STATUS_UNKNOWN}，且<b>不</b>做后续 User /
 *       Tenant 查询，杜绝"通过响应体大小或时延差异枚举有效邀请码"的侧信道。
 *   <li>命中场景仅回显脱敏 hint：{@code tenantKeyHint = 前 3 字符 + "…"}，{@code usernameHint = 首字符 +
 *       "***"}；<b>不</b>回显 inviteId / userId / passwordHash / 完整 tenantKey / 完整 username。
 *   <li>{@code expiresAt} 仅在 {@code STATUS_PENDING} 时暴露（供前端显示倒计时）；其它态返回 {@code null}。
 * </ul>
 *
 * <p><b>只读</b>：不写审计、不修改邀请状态——惰性过期不主动写库，由 {@link UserInvite#isUsable} 在消费侧判定； 本查询只是把这一判定"外化"给前端。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Component
@Slf4j
public class InviteStatusQryExe {

    /** tenantKey 前缀可见长度（脱敏 hint）。 */
    private static final int TENANT_KEY_HINT_LENGTH = 3;

    /** tenantKey 短于该长度时不脱敏直接返回原值（极小租户场景下 3 字符已足够标识）。 */
    private static final int TENANT_KEY_MIN_HINTABLE_LENGTH = 6;

    private final UserInviteService userInviteService;
    private final UserGateway userGateway;
    private final TenantGateway tenantGateway;

    public InviteStatusQryExe(
            UserInviteService userInviteService,
            UserGateway userGateway,
            TenantGateway tenantGateway) {
        this.userInviteService = userInviteService;
        this.userGateway = userGateway;
        this.tenantGateway = tenantGateway;
    }

    /**
     * 按 raw code 查询邀请状态。
     *
     * @param rawCode 原始邀请码（前端从 URL query 取值原样传入）
     * @return 状态视图；{@code rawCode} 为空或全空白时按 {@link InviteStatusVO#STATUS_UNKNOWN} 处理
     */
    public InviteStatusVO execute(String rawCode) {
        Optional<UserInvite> found = userInviteService.findByRawCode(rawCode);
        if (found.isEmpty()) {
            // 未命中：不查 User / Tenant 也不 log rawCode，避免侧信道枚举与红线 #4 泄漏
            return InviteStatusVO.unknown();
        }
        UserInvite invite = found.get();
        LocalDateTime now = LocalDateTime.now();

        InviteStatusVO vo = new InviteStatusVO();
        vo.setTenantKeyHint(maskTenantKey(invite.getTenantId()));
        vo.setUsernameHint(maskUsername(invite.getUserId()));

        String status = invite.getStatus();
        if (UserInvite.STATUS_USED.equals(status)) {
            vo.setStatus(InviteStatusVO.STATUS_USED);
            vo.setHint("该邀请码已完成设密，请使用新密码登录");
            return vo;
        }
        if (UserInvite.STATUS_REVOKED.equals(status)) {
            vo.setStatus(InviteStatusVO.STATUS_REVOKED);
            vo.setHint("该邀请码已被吊销或重签，请向平台运维申请新链接");
            return vo;
        }
        // status == PENDING 但可能已过期
        if (invite.isExpired(now)) {
            vo.setStatus(InviteStatusVO.STATUS_EXPIRED);
            vo.setHint("该邀请码已过期，请向平台运维申请新链接");
            return vo;
        }
        vo.setStatus(InviteStatusVO.STATUS_PENDING);
        vo.setExpiresAt(invite.getExpiresAt());
        vo.setHint("请设置新密码完成激活");
        return vo;
    }

    /**
     * tenantKey 脱敏：前 {@value #TENANT_KEY_HINT_LENGTH} 字符 + {@code …}；短标识或空租户（未 seed）回退 {@code "?"}。
     */
    private String maskTenantKey(Long tenantId) {
        if (tenantId == null) {
            return "?";
        }
        return tenantGateway
                .findById(tenantId)
                .map(Tenant::getTenantKey)
                .map(InviteStatusQryExe::maskTenantKeyLiteral)
                .orElse("?");
    }

    private static String maskTenantKeyLiteral(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            return "?";
        }
        if (tenantKey.length() < TENANT_KEY_MIN_HINTABLE_LENGTH) {
            return tenantKey;
        }
        return tenantKey.substring(0, TENANT_KEY_HINT_LENGTH) + "…";
    }

    /** username 脱敏：首字符 + {@code ***}；查不到或为空回退 {@code "?"}。 */
    private String maskUsername(Long userId) {
        if (userId == null) {
            return "?";
        }
        return userGateway
                .findById(userId)
                .map(User::getUsername)
                .map(InviteStatusQryExe::maskUsernameLiteral)
                .orElse("?");
    }

    private static String maskUsernameLiteral(String username) {
        if (username == null || username.isEmpty()) {
            return "?";
        }
        return username.charAt(0) + "***";
    }
}
