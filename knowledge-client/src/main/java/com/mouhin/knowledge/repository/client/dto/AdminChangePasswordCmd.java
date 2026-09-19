package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理端修改密码命令（自助改密：校验旧密码后设置新密码）。
 *
 * <p>userKey 由适配层从已认证令牌回填，<b>不信任</b>请求体传入，防止越权改他人密码。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@Getter
@Setter
public class AdminChangePasswordCmd {

    /** 目标用户唯一标识（由服务端从令牌回填）。 */
    private String userKey;

    /** 旧密码，用于校验身份。 */
    private String oldPassword;

    /** 新密码（明文，服务端 BCrypt 后落库）。 */
    private String newPassword;
}
