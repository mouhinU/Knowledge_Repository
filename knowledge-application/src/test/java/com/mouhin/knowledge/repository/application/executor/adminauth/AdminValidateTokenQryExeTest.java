package com.mouhin.knowledge.repository.application.executor.adminauth;

import com.mouhin.knowledge.repository.client.dto.AdminPrincipalDTO;
import com.mouhin.knowledge.repository.domain.gateway.AdminJwtService;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.domain.model.valueobject.AdminTokenPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 管理端令牌校验查询执行器单测：锁定"双重判定"契约 —— JWT 签名通过 + 账号 ACTIVE 才回主体； 任一环节失败即返回 null（不抛）由调用方映射 401；同时校验
 * departmentId 已从 User 完整回写到 AdminPrincipalDTO。
 *
 * @author mouhinU
 * @date 2026-09-24 16:34:00
 */
@DisplayName("管理端令牌校验查询执行器 (AdminValidateTokenQryExe)")
class AdminValidateTokenQryExeTest {

    private final AdminJwtService adminJwtService = mock(AdminJwtService.class);
    private final UserGateway userGateway = mock(UserGateway.class);
    private final AdminValidateTokenQryExe exe =
            new AdminValidateTokenQryExe(adminJwtService, userGateway);

    private AdminTokenPayload payload(String userKey) {
        return new AdminTokenPayload(userKey, "admin", true, Instant.now().plusSeconds(3600));
    }

    private User activeUser(String userKey, boolean admin, String dept) {
        User u = new User();
        u.setUserKey(userKey);
        u.setUsername("admin");
        u.setStatus(User.STATUS_ACTIVE);
        u.setAdmin(admin);
        u.setDepartmentId(dept);
        return u;
    }

    @Test
    @DisplayName("token null / 空白 → 立即返回 null，不做 verify / 不查库")
    void blankTokenShortCircuitsToNull() {
        assertThat(exe.execute(null)).isNull();
        assertThat(exe.execute("  ")).isNull();
        verify(adminJwtService, never()).verify(anyString());
        verify(userGateway, never()).findByUserKey(anyString());
    }

    @Test
    @DisplayName("JWT 校验失败（签名/过期） → null，不再回库确认账号状态")
    void invalidJwtReturnsNull() {
        when(adminJwtService.verify("bad")).thenReturn(Optional.empty());

        assertThat(exe.execute("bad")).isNull();
        verify(userGateway, never()).findByUserKey(anyString());
    }

    @Test
    @DisplayName("JWT 有效但用户不存在 → null（令牌吊销 / 用户被删场景）")
    void userGoneReturnsNull() {
        when(adminJwtService.verify("ok")).thenReturn(Optional.of(payload("key-1")));
        when(userGateway.findByUserKey("key-1")).thenReturn(Optional.empty());

        assertThat(exe.execute("ok")).isNull();
    }

    @Test
    @DisplayName("JWT 有效但账号被禁用 → null（弥补 JWT 无状态无法主动失效）")
    void disabledAccountReturnsNull() {
        when(adminJwtService.verify("ok")).thenReturn(Optional.of(payload("key-1")));
        User u = activeUser("key-1", true, "D-1");
        u.setStatus(User.STATUS_DISABLED);
        when(userGateway.findByUserKey("key-1")).thenReturn(Optional.of(u));

        assertThat(exe.execute("ok")).isNull();
    }

    @Test
    @DisplayName("正常路径 → 回 AdminPrincipalDTO，含 userKey/username/admin/departmentId")
    void happyPathReturnsPrincipalWithDeptScope() {
        when(adminJwtService.verify("ok")).thenReturn(Optional.of(payload("key-1")));
        when(userGateway.findByUserKey("key-1"))
                .thenReturn(Optional.of(activeUser("key-1", true, "D-42")));

        AdminPrincipalDTO dto = exe.execute("ok");

        assertThat(dto).isNotNull();
        assertThat(dto.getUserKey()).isEqualTo("key-1");
        assertThat(dto.getUsername()).isEqualTo("admin");
        assertThat(dto.getAdmin()).isTrue();
        assertThat(dto.getDepartmentId()).isEqualTo("D-42");
    }

    @Test
    @DisplayName("非 admin 用户 → admin=false 仍回主体（是否放行由上层权限判断）")
    void nonAdminFlagPreserved() {
        when(adminJwtService.verify("ok")).thenReturn(Optional.of(payload("key-2")));
        when(userGateway.findByUserKey("key-2"))
                .thenReturn(Optional.of(activeUser("key-2", false, null)));

        AdminPrincipalDTO dto = exe.execute("ok");

        assertThat(dto).isNotNull();
        assertThat(dto.getAdmin()).isFalse();
        assertThat(dto.getDepartmentId()).isNull();
    }

    @Test
    @DisplayName("P0-T2：payload.roles 非空 → principal.roles 原样透传，无需回库")
    void principalCarriesRolesFromClaim() {
        AdminTokenPayload withRoles =
                new AdminTokenPayload(
                        "key-3",
                        "admin",
                        true,
                        List.of("ADMIN", "AUDITOR"),
                        Instant.now().plusSeconds(3600));
        when(adminJwtService.verify("ok")).thenReturn(Optional.of(withRoles));
        when(userGateway.findByUserKey("key-3"))
                .thenReturn(Optional.of(activeUser("key-3", true, "D-3")));

        AdminPrincipalDTO dto = exe.execute("ok");

        assertThat(dto).isNotNull();
        assertThat(dto.getRoles()).containsExactly("ADMIN", "AUDITOR");
    }

    @Test
    @DisplayName("P0-T2：旧 4 参 payload → principal.roles 归一为空列表（兼容旧令牌）")
    void legacyPayloadNormalizesToEmptyRoles() {
        when(adminJwtService.verify("ok")).thenReturn(Optional.of(payload("key-4")));
        when(userGateway.findByUserKey("key-4"))
                .thenReturn(Optional.of(activeUser("key-4", true, "D-4")));

        AdminPrincipalDTO dto = exe.execute("ok");

        assertThat(dto).isNotNull();
        assertThat(dto.getRoles()).isEmpty();
    }
}
