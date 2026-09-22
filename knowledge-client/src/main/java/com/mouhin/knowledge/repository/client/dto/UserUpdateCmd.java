package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 修改用户命令（字段为 null 表示不修改，沿用既有语义）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Getter
@Setter
public class UserUpdateCmd {

    private String userKey;
    private String username;
    private String departmentId;
    private Boolean admin;

    /** 可选：重置登录密码（明文，app 层 BCrypt 后落库）；为 null 表示不修改密码。 */
    private String password;

    /** 可选：账号状态 ACTIVE / DISABLED；为 null 表示不修改状态。 */
    private String status;
}
