package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 修改用户命令（字段为 null 表示不修改，沿用既有语义）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Getter
@Setter
public class UserUpdateCmd {

    private String userKey;
    private String username;
    private String departmentId;
    private Boolean admin;
}
