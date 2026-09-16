package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 权限检查值对象
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
public final class Permission {

    /**
     * 用户 ID
     */
    private final String userId;

    /**
     * 用户所属部门 ID
     */
    private final String departmentId;

    /**
     * 用户角色列表（逗号分隔）
     */
    private final String roles;

    /**
     * 是否为超级管理员
     */
    private final boolean admin;

    public Permission(String userId, String departmentId, String roles, boolean admin) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        this.userId = userId;
        this.departmentId = departmentId;
        this.roles = roles;
        this.admin = admin;
    }

    public String getUserId() {
        return userId;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public String getRoles() {
        return roles;
    }

    public boolean isAdmin() {
        return admin;
    }
}
