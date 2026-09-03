package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 部门实体
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
public class Department {

    private Long id;

    /** 部门唯一标识（UUID） */
    private String departmentKey;

    /** 部门名称 */
    private String departmentName;

    /** 父部门 ID（null 表示顶级部门） */
    private Long parentId;

    private LocalDateTime createdTime;

    // ==================== 业务方法 ====================

    /**
     * 判断是否为顶级部门
     */
    public boolean isTopLevel() {
        return parentId == null;
    }

    // ==================== Getters & Setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDepartmentKey() {
        return departmentKey;
    }

    public void setDepartmentKey(String departmentKey) {
        this.departmentKey = departmentKey;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
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
        Department that = (Department) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Department{" +
                "id=" + id +
                ", departmentKey='" + departmentKey + '\'' +
                ", departmentName='" + departmentName + '\'' +
                ", parentId=" + parentId +
                '}';
    }
}
