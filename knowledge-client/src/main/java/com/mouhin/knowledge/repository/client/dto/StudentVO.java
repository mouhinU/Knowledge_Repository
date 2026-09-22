package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 考生视图对象
 *
 * <p>字段语义与原 {@code StudentAuthController.me} 一致：studentId（Long）、username、
 * displayName（null→username）、studentNo（null→""）。不携带密码 / 令牌等敏感字段。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Getter
@Setter
public class StudentVO {

    private Long studentId;
    private String username;
    private String displayName;
    private String studentNo;
}
