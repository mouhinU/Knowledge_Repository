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
}
