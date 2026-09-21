package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理端登录命令
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@Getter
@Setter
public class AdminLoginCmd {

    private String username;
    private String password;
}
