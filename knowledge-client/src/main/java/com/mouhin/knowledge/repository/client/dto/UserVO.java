package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 用户视图对象
 *
 * <p>字段与原 {@code UserAdminController.toResponse} 完全一致：userKey、username、
 * departmentId（null→""）、admin（null→false）、createdTime（LocalDateTime#toString，null→""）。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Getter
@Setter
public class UserVO {

    private String userKey;
    private String username;
    private String departmentId;
    private Boolean admin;
    private String createdTime;
}
