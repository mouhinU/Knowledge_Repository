package com.mouhin.knowledge.repository.domain.service;

import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 权限领域服务
 *
 * <p>负责构建 Milvus 查询过滤表达式，实现 RBAC + 文档级 ACL 权限隔离。
 *
 * @author mouhinU
 * @date 2026-09-02
 */
@Service
@Slf4j
public class PermissionDomainService {

    /**
     * 根据用户权限构建 Milvus 过滤表达式
     *
     * <p>权限规则： 1. 超级管理员：无过滤 2. PUBLIC 文档：所有已认证用户可访问 3. INTERNAL 文档：同部门用户可访问 4. RESTRICTED
     * 文档：文档允许的角色列表包含用户角色 5. PRIVATE 文档：仅文档所有者可访问
     *
     * @param permission 用户权限上下文
     * @return Milvus 过滤表达式字符串，null 表示不过滤
     */
    public String buildFilterExpression(Permission permission) {
        if (permission == null) {
            throw new IllegalArgumentException("permission must not be null");
        }

        // 超级管理员：不过滤
        if (permission.isAdmin()) {
            log.debug("Admin user {}, no filter applied", permission.getUserId());
            return null;
        }

        // 构建 OR 条件
        List<String> conditions = new java.util.ArrayList<>();

        // 条件1: 公开文档
        conditions.add("visibility == \"PUBLIC\"");

        // 条件2: 内部文档 + 同部门
        if (permission.getDepartmentId() != null && !permission.getDepartmentId().isBlank()) {
            conditions.add(
                    String.format(
                            "(visibility == \"INTERNAL\" and department_id == \"%s\")",
                            escapeMilvusValue(permission.getDepartmentId())));
        }

        // 条件3: 受限文档 + 用户角色在允许列表中
        if (permission.getRoles() != null && !permission.getRoles().isBlank()) {
            // allowed_roles 存储为 JSON 数组字符串如 ["ADMIN","MANAGER"]
            // Milvus 不支持 LIKE，使用多个精确匹配
            String[] roles = permission.getRoles().split(",");
            for (String role : roles) {
                String trimmedRole = role.trim();
                if (!trimmedRole.isEmpty()) {
                    conditions.add(
                            String.format(
                                    "(visibility == \"RESTRICTED\" and allowed_roles like \"%%%s%%\")",
                                    escapeMilvusValue(trimmedRole)));
                }
            }
        }

        // 条件4: 私有文档 + 自己是所有者
        conditions.add(
                String.format(
                        "(visibility == \"PRIVATE\" and owner_id == \"%s\")",
                        escapeMilvusValue(permission.getUserId())));

        String expression =
                String.join(
                        " or ", conditions.stream().map(c -> "(" + c + ")").toArray(String[]::new));

        log.debug("Permission filter for user {}: {}", permission.getUserId(), expression);
        return expression;
    }

    /** 检查用户是否有权访问指定文档 */
    public boolean hasAccess(
            Permission permission,
            DocumentVisibilityEnum visibility,
            String documentOwnerId,
            String documentDepartmentId,
            String documentAllowedRoles) {
        if (permission.isAdmin()) {
            return true;
        }

        return switch (visibility) {
            case PUBLIC -> true;
            case INTERNAL ->
                    permission.getDepartmentId() != null
                            && permission.getDepartmentId().equals(documentDepartmentId);
            case RESTRICTED -> checkRoleAccess(permission.getRoles(), documentAllowedRoles);
            case PRIVATE -> permission.getUserId().equals(documentOwnerId);
        };
    }

    /** 检查用户角色是否在文档允许的角色列表中 */
    private boolean checkRoleAccess(String userRoles, String documentAllowedRoles) {
        if (userRoles == null || documentAllowedRoles == null) {
            return false;
        }
        List<String> userRoleList = Arrays.asList(userRoles.split(","));
        List<String> allowedRoleList = Arrays.asList(documentAllowedRoles.split(","));

        return userRoleList.stream()
                .map(String::trim)
                .anyMatch(
                        role ->
                                allowedRoleList.stream()
                                        .map(String::trim)
                                        .anyMatch(allowed -> allowed.equals(role)));
    }

    /** 转义 Milvus 表达式中的特殊字符 */
    private String escapeMilvusValue(String value) {
        if (value == null) {
            return "";
        }
        // 顺序敏感：先转义反斜杠，再转义双引号。
        // 若先转义引号，引号转义新插入的反斜杠会被随后的反斜杠转义再次翻倍，
        // 破坏含引号 / 反斜杠的值（a"b → a\\"b 而非期望的 a\"b）。
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
