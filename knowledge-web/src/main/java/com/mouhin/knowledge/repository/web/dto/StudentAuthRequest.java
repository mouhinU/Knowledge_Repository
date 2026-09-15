package com.mouhin.knowledge.repository.web.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 考生注册 / 登录请求
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Getter
@Setter
public class StudentAuthRequest {

    private String username;

    private String password;

    private String displayName;

    private String studentNo;
}
