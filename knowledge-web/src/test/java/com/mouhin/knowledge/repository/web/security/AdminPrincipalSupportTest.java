package com.mouhin.knowledge.repository.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.AdminPrincipalDTO;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 管理端主体 → 权限转换单测：锁定 {@code toPermission} 的可信身份口径 —— 字段从 principal 回读（userId / departmentId /
 * roles）、admin 恒真、roles 空列表归一为 null 保持旧契约。principal 缺失 / 类型不符 / userKey 空白 / 非 admin 一律抛 {@link
 * AdminAuthRequiredException}（HTTP 401 语义），绝不回退请求体。
 *
 * <p>P0-T2 起 roles 由令牌 {@code roles} claim 直读；空列表 / null 统一映射到 Permission 的 {@code null}， 与既有"null
 * / 空 = 不过滤"的下游语义一致。
 *
 * @author mouhinU
 * @date 2026-09-24 18:16:00
 */
@DisplayName("管理端主体权限转换 (AdminPrincipalSupport.toPermission)")
class AdminPrincipalSupportTest {

    private final HttpServletRequest request = mock(HttpServletRequest.class);

    private void stubAttribute(Object attribute) {
        when(request.getAttribute(AdminTokenAuthFilter.PRINCIPAL_ATTRIBUTE)).thenReturn(attribute);
    }

    @Test
    @DisplayName("合法管理员主体（未带 roles）→ Permission 字段齐全：roles 归一为 null 保持旧契约")
    void validAdminPrincipalConverts() {
        stubAttribute(new AdminPrincipalDTO("u-100", "boss", true, "dept-07"));

        Permission permission = AdminPrincipalSupport.toPermission(request);

        assertEquals("u-100", permission.getUserId());
        assertEquals("dept-07", permission.getDepartmentId());
        assertTrue(permission.isAdmin());
        assertNull(permission.getRoles(), "P0-T2：空 roles 仍归一为 null");
    }

    @Test
    @DisplayName("P0-T2：principal.roles 非空 → Permission.roles 逗号拼接回读")
    void validAdminPrincipalWithRolesJoins() {
        stubAttribute(
                new AdminPrincipalDTO(
                        "u-200", "auditor", true, "dept-08", List.of("ADMIN", "AUDITOR", "OPS")));

        Permission permission = AdminPrincipalSupport.toPermission(request);

        assertEquals("ADMIN,AUDITOR,OPS", permission.getRoles());
        assertEquals("u-200", permission.getUserId());
        assertEquals("dept-08", permission.getDepartmentId());
        assertTrue(permission.isAdmin());
    }

    @Test
    @DisplayName("P0-T2：principal.roles 显式空列表 → 依旧归一为 null（不产出空串）")
    void emptyRolesListNormalizesToNull() {
        stubAttribute(new AdminPrincipalDTO("u-300", "quiet", true, "dept-09", List.<String>of()));

        Permission permission = AdminPrincipalSupport.toPermission(request);

        assertNull(permission.getRoles(), "空列表必须归一为 null，避免下游 'contains(\",\")' 误判");
    }

    @Test
    @DisplayName("attribute 为 null（过滤器未写入主体）→ AdminAuthRequiredException")
    void nullAttributeRejected() {
        stubAttribute(null);

        AdminAuthRequiredException ex =
                assertThrows(
                        AdminAuthRequiredException.class,
                        () -> AdminPrincipalSupport.toPermission(request));
        assertTrue(ex.getMessage().contains("管理端身份不可用"));
    }

    @Test
    @DisplayName("attribute 非 AdminPrincipalDTO 类型 → 拒绝（模式匹配失败分支）")
    void wrongTypeAttributeRejected() {
        stubAttribute(" forged-string ");

        assertThrows(
                AdminAuthRequiredException.class,
                () -> AdminPrincipalSupport.toPermission(request));
    }

    @Test
    @DisplayName("userKey null / 空白 → 拒绝，不得凭空标识放行")
    void blankUserKeyRejected() {
        stubAttribute(new AdminPrincipalDTO(null, "ghost", true));
        assertThrows(
                AdminAuthRequiredException.class,
                () -> AdminPrincipalSupport.toPermission(request));

        stubAttribute(new AdminPrincipalDTO("   ", "ghost", true));
        assertThrows(
                AdminAuthRequiredException.class,
                () -> AdminPrincipalSupport.toPermission(request));
    }

    @Test
    @DisplayName("principal 非管理员（admin=false / admin=null）→ 拒绝（考生令牌冒充管理端口径）")
    void nonAdminPrincipalRejected() {
        stubAttribute(new AdminPrincipalDTO("u-1", "student", false, "dept-01"));
        assertThrows(
                AdminAuthRequiredException.class,
                () -> AdminPrincipalSupport.toPermission(request));

        stubAttribute(new AdminPrincipalDTO("u-1", "student", null, "dept-01"));
        assertThrows(
                AdminAuthRequiredException.class,
                () -> AdminPrincipalSupport.toPermission(request));
    }
}
