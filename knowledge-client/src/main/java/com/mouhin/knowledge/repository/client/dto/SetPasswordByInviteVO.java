package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 邀请式设密结果（client 层出参，P3-T3）。
 *
 * <p>仅返回操作是否成功与用户可回显信息（username / tenantKey），<b>不</b>返回任何令牌； 设密完成后管理员须走标准 {@code POST
 * /api/admin/auth/login} 换正式 admin token。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Getter
@Setter
public class SetPasswordByInviteVO {

    /**
     * 是否成功。
     */
    private boolean success;

    /**
     * 完成设密的用户名（如 {@code acme-admin}），供前端在成功页展示。
     */
    private String username;

    /**
     * 用户所属租户 key，供前端跳转到对应租户的登录页。
     */
    private String tenantKey;

    /**
     * 提示消息，如"密码已设置，请使用新密码登录"。
     */
    private String message;
}
