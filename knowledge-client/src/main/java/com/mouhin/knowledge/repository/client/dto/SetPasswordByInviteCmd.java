package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 邀请式设密命令（client 层入参，P3-T3）。
 *
 * <p>由 {@code POST /api/admin/auth/set-password-by-invite} 端点接收；<b>无需登录令牌</b>（凭 raw invite code
 * 换取设密权限），故在 {@code AdminTokenAuthFilter.PUBLIC_PREFIXES} 白名单里。
 *
 * <p><b>安全纪律</b>：{@code inviteCode} 与 {@code newPassword} 均为敏感入参，Controller / Executor 全链 禁止 {@code
 * log.toString()} / {@code log.info("{}", cmd)}；只可脱敏记录（如 hash 前 8 位）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Getter
@Setter
public class SetPasswordByInviteCmd {

    /**
     * 原始邀请码（Base64URL 无填充，43 字符），由 provision 响应体一次性下发。
     */
    private String inviteCode;

    /**
     * 新密码明文；服务端以 BCrypt 哈希后落 {@code sys_user.password_hash}。
     */
    private String newPassword;
}
