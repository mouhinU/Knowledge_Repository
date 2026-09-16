package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 文档可见性枚举
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
public enum DocumentVisibilityEnum {

    /**
     * 公开：所有已认证用户可访问
     */
    PUBLIC,

    /**
     * 内部：同部门用户可访问
     */
    INTERNAL,

    /**
     * 受限：仅指定角色/用户可访问
     */
    RESTRICTED,

    /**
     * 私有：仅文档所有者可访问
     */
    PRIVATE
}
