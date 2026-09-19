package com.mouhin.knowledge.repository.application.executor.adminauth;

import com.mouhin.knowledge.repository.client.dto.AdminLoginCmd;
import com.mouhin.knowledge.repository.client.dto.AdminLoginResultDTO;
import com.mouhin.knowledge.repository.domain.gateway.AdminJwtService;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.domain.model.valueobject.AdminTokenPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 管理端登录执行器单元测试。
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@DisplayName("AdminLoginCmdExe 登录编排")
class AdminLoginCmdExeTest {

    private static final String HASH = "$2a$10$dummydummydummydummydummydummydummydummydummydummydummydum";

    private UserGateway userGateway;
    private BCryptPasswordEncoder passwordEncoder;
    private AdminJwtService adminJwtService;
    private AdminLoginCmdExe exe;

    @BeforeEach
    void setUp() {
        userGateway = mock(UserGateway.class);
        passwordEncoder = mock(BCryptPasswordEncoder.class);
        adminJwtService = mock(AdminJwtService.class);
        exe = new AdminLoginCmdExe(userGateway, passwordEncoder, adminJwtService);
    }

    private User activeAdmin() {
        User u = new User();
        u.setUserKey("key-1");
        u.setUsername("admin");
        u.setAdmin(true);
        u.setPasswordHash(HASH);
        u.setStatus(User.STATUS_ACTIVE);
        return u;
    }

    @Test
    @DisplayName("凭证正确且账号激活时签发令牌并返回身份")
    void success() {
        when(userGateway.findByUsername("admin")).thenReturn(Optional.of(activeAdmin()));
        when(passwordEncoder.matches("pass", HASH)).thenReturn(true);
        when(adminJwtService.issue(eq("key-1"), eq("admin"), eq(true))).thenReturn("jwt-token");
        when(adminJwtService.verify("jwt-token")).thenReturn(
                Optional.of(new AdminTokenPayload("key-1", "admin", true, Instant.now().plusSeconds(3600))));

        AdminLoginCmd cmd = new AdminLoginCmd();
        cmd.setUsername("admin");
        cmd.setPassword("pass");

        AdminLoginResultDTO result = exe.execute(cmd);
        assertEquals("jwt-token", result.getToken());
        assertEquals("key-1", result.getUserKey());
        assertEquals("admin", result.getUsername());
        assertTrue(result.getAdmin());
        assertNotNull(result.getExpiresAt());
        assertTrue(result.getExpiresAt().isBlank() == false);
    }

    @Test
    @DisplayName("密码错误应拒绝（用户名或密码错误）")
    void wrongPassword() {
        when(userGateway.findByUsername("admin")).thenReturn(Optional.of(activeAdmin()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        AdminLoginCmd cmd = new AdminLoginCmd();
        cmd.setUsername("admin");
        cmd.setPassword("bad");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> exe.execute(cmd));
        assertEquals("用户名或密码错误", ex.getMessage());
    }

    @Test
    @DisplayName("账号禁用应拒绝（账号已被禁用）")
    void disabledAccount() {
        User u = activeAdmin();
        u.setStatus(User.STATUS_DISABLED);
        when(userGateway.findByUsername("admin")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pass", HASH)).thenReturn(true);

        AdminLoginCmd cmd = new AdminLoginCmd();
        cmd.setUsername("admin");
        cmd.setPassword("pass");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> exe.execute(cmd));
        assertEquals("账号已被禁用", ex.getMessage());
    }

    @Test
    @DisplayName("未设置密码的账号应按密码错误拒绝（不可枚举）")
    void accountWithoutPassword() {
        User u = activeAdmin();
        u.setPasswordHash(null);
        when(userGateway.findByUsername("admin")).thenReturn(Optional.of(u));

        AdminLoginCmd cmd = new AdminLoginCmd();
        cmd.setUsername("admin");
        cmd.setPassword("pass");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> exe.execute(cmd));
        assertEquals("用户名或密码错误", ex.getMessage());
    }

    @Test
    @DisplayName("用户名不存在应拒绝，且不触碰密码比对")
    void userNotFound() {
        when(userGateway.findByUsername("ghost")).thenReturn(Optional.empty());

        AdminLoginCmd cmd = new AdminLoginCmd();
        cmd.setUsername("ghost");
        cmd.setPassword("pass");

        assertThrows(IllegalArgumentException.class, () -> exe.execute(cmd));
        org.mockito.Mockito.verify(passwordEncoder, org.mockito.Mockito.never())
                .matches(anyString(), anyString());
        org.mockito.Mockito.verify(adminJwtService, org.mockito.Mockito.never())
                .issue(anyString(), anyString(), anyBoolean());
    }
}
