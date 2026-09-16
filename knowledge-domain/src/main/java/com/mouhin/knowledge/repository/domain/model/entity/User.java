package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 用户实体
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
public class User {

    private Long id;

    /**
     * 用户唯一标识（UUID）
     */
    private String userKey;

    /**
     * 用户名
     */
    private String username;

    /**
     * 所属部门 ID
     */
    private String departmentId;

    /**
     * 是否为超级管理员
     */
    private Boolean admin;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    // ==================== Getters & Setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserKey() {
        return userKey;
    }

    public void setUserKey(String userKey) {
        this.userKey = userKey;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public Boolean getAdmin() {
        return admin;
    }

    public void setAdmin(Boolean admin) {
        this.admin = admin;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", userKey='" + userKey + '\'' +
                ", username='" + username + '\'' +
                ", departmentId='" + departmentId + '\'' +
                ", admin=" + admin +
                '}';
    }
}
