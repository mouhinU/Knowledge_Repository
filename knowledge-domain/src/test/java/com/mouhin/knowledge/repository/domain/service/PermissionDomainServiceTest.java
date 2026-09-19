package com.mouhin.knowledge.repository.domain.service;

import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PermissionDomainService} 回归测试。
 * <p>
 * 重点锁定 escapeMilvusValue 的转义顺序缺陷：曾先转义双引号、后转义反斜杠，
 * 导致引号转义新插入的反斜杠被再次翻倍，破坏含引号 / 反斜杠的值，进而可能使
 * Milvus 权限过滤表达式失效（ACL 越权风险）。此处通过公开入口 buildFilterExpression
 * 验证嵌入到过滤条件中的值被正确转义。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@DisplayName("PermissionDomainService Milvus 过滤表达式")
class PermissionDomainServiceTest {

    private final PermissionDomainService service = new PermissionDomainService();

    @Test
    @DisplayName("超级管理员不加过滤，返回 null")
    void adminReturnsNullFilter() {
        Permission admin = new Permission("u-admin", "dept-x", "ADMIN", true);
        assertNull(service.buildFilterExpression(admin));
    }

    @Test
    @DisplayName("null 权限抛 IllegalArgumentException")
    void nullPermissionThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.buildFilterExpression(null));
    }

    @Nested
    @DisplayName("值转义（防双转义回归）")
    class ValueEscaping {

        @Test
        @DisplayName("含双引号的部门 ID 只转义一次：a\"b → a\\\"b")
        void quoteEscapedOnce() {
            // 普通用户：PUBLIC 恒真，INTERNAL 分支嵌入部门 ID，PRIVATE 分支嵌入用户 ID
            Permission p = new Permission("u1", "eng\"hr", null, false);
            String expr = service.buildFilterExpression(p);
            assertTrue(expr.contains("department_id == \"eng\\\"hr\""),
                    "期望部门值被转义为 eng\\\"hr，实际：" + expr);
        }

        @Test
        @DisplayName("含反斜杠的部门 ID 被转义为双反斜杠")
        void backslashEscaped() {
            Permission p = new Permission("u1", "a\\b", null, false);
            String expr = service.buildFilterExpression(p);
            assertTrue(expr.contains("department_id == \"a\\\\b\""),
                    "期望部门值被转义为 a\\\\b，实际：" + expr);
        }

        @Test
        @DisplayName("含反斜杠 + 引号的组合不被二次翻倍")
        void backslashThenQuote() {
            // 输入 a\ " (一个反斜杠紧跟一个引号)
            Permission p = new Permission("u1", "a\\\"b", null, false);
            String expr = service.buildFilterExpression(p);
            // 期望：反斜杠→\\，引号→\"，整体 a\\\"b
            assertTrue(expr.contains("department_id == \"a\\\\\\\"b\""),
                    "组合转义应各自恰好一次，实际：" + expr);
        }

        @Test
        @DisplayName("受限角色值同样经过转义")
        void roleValueEscaped() {
            Permission p = new Permission("u1", null, "ADMIN\"X", false);
            String expr = service.buildFilterExpression(p);
            assertTrue(expr.contains("allowed_roles like \"%ADMIN\\\"X%\""),
                    "角色值应被转义，实际：" + expr);
        }
    }

    @Test
    @DisplayName("私有文档按所有者过滤")
    void privateFilterUsesOwnerId() {
        Permission p = new Permission("owner-9", "dept-a", "MEMBER", false);
        String expr = service.buildFilterExpression(p);
        assertTrue(expr.contains("visibility == \"PRIVATE\""));
        assertTrue(expr.contains("owner_id == \"owner-9\""));
    }

    @Nested
    @DisplayName("hasAccess 文档级 ACL 判定（SEC-1 检索后置过滤复用）")
    class HasAccess {

        @Test
        @DisplayName("超级管理员放行任意可见性文档")
        void adminBypassesAll() {
            Permission admin = new Permission("u-admin", "dept-x", "ADMIN", true);
            assertTrue(service.hasAccess(admin, DocumentVisibilityEnum.PRIVATE, "someone-else", "other-dept", "MANAGER"));
            assertTrue(service.hasAccess(admin, DocumentVisibilityEnum.RESTRICTED, "o", "d", "NONE"));
        }

        @Test
        @DisplayName("PUBLIC 对所有已认证用户放行")
        void publicAlwaysAllowed() {
            Permission u = new Permission("u1", "dept-a", "STUDENT", false);
            assertTrue(service.hasAccess(u, DocumentVisibilityEnum.PUBLIC, "owner-x", "dept-y", null));
        }

        @Test
        @DisplayName("INTERNAL 同部门放行、跨部门拒绝")
        void internalByDepartment() {
            Permission u = new Permission("u1", "dept-a", "STUDENT", false);
            assertTrue(service.hasAccess(u, DocumentVisibilityEnum.INTERNAL, "owner-x", "dept-a", null));
            assertFalse(service.hasAccess(u, DocumentVisibilityEnum.INTERNAL, "owner-x", "dept-b", null));
        }

        @Test
        @DisplayName("INTERNAL 用户无部门时拒绝")
        void internalNullDepartmentDenied() {
            Permission u = new Permission("u1", null, "STUDENT", false);
            assertFalse(service.hasAccess(u, DocumentVisibilityEnum.INTERNAL, "owner-x", "dept-a", null));
        }

        @Test
        @DisplayName("RESTRICTED 用户角色命中允许列表放行（逗号分隔）")
        void restrictedRoleMatch() {
            Permission u = new Permission("u1", "dept-a", "TEACHER,LEADER", false);
            assertTrue(service.hasAccess(u, DocumentVisibilityEnum.RESTRICTED, "o", "d", "ADMIN,TEACHER"));
        }

        @Test
        @DisplayName("RESTRICTED 用户角色不在允许列表拒绝")
        void restrictedRoleMismatch() {
            Permission u = new Permission("u1", "dept-a", "STUDENT", false);
            assertFalse(service.hasAccess(u, DocumentVisibilityEnum.RESTRICTED, "o", "d", "TEACHER,MANAGER"));
        }

        @Test
        @DisplayName("RESTRICTED 用户无角色时拒绝")
        void restrictedNullRolesDenied() {
            Permission u = new Permission("u1", "dept-a", null, false);
            assertFalse(service.hasAccess(u, DocumentVisibilityEnum.RESTRICTED, "o", "d", "TEACHER"));
        }

        @Test
        @DisplayName("PRIVATE 仅所有者放行、他人拒绝")
        void privateByOwner() {
            Permission u = new Permission("u1", "dept-a", "MEMBER", false);
            assertTrue(service.hasAccess(u, DocumentVisibilityEnum.PRIVATE, "u1", "dept-a", null));
            assertFalse(service.hasAccess(u, DocumentVisibilityEnum.PRIVATE, "u2", "dept-a", null));
        }
    }
}
