package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 新增用户命令
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Getter
@Setter
public class UserCreateCmd {

    private String username;
    private String departmentId;
    private Boolean admin;

    /** 可选：初始登录密码（明文，app 层 BCrypt 后落库）；为空表示该账号暂不可密码登录。 */
    private String password;
}
