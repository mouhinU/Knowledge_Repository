package com.mouhin.knowledge.repository.application.executor.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.UserCreateCmd;
import com.mouhin.knowledge.repository.client.dto.UserVO;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 新增用户命令执行器单测：锁定唯一性冲突、password 明文不落库（走 BCrypt）、 admin null → false 默认、status 强制为 ACTIVE，以及成功路径返回的
 * VO 结构。
 *
 * @author mouhinU
 * @date 2026-09-24 16:37:00
 */
@DisplayName("新增用户命令执行器 (UserCreateCmdExe)")
class UserCreateCmdExeTest {

    private final UserGateway userGateway = mock(UserGateway.class);
    private final BCryptPasswordEncoder passwordEncoder = mock(BCryptPasswordEncoder.class);
    private final UserCreateCmdExe exe = new UserCreateCmdExe(userGateway, passwordEncoder);

    private UserCreateCmd cmd(String username, String dept, Boolean admin, String pwd) {
        UserCreateCmd c = new UserCreateCmd();
        c.setUsername(username);
        c.setDepartmentId(dept);
        c.setAdmin(admin);
        c.setPassword(pwd);
        return c;
    }

    @Test
    @DisplayName("用户名已存在 → 'Username already exists'，不落库")
    void duplicateUsernameRejected() {
        when(userGateway.findByUsername("bob")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> exe.execute(cmd("bob", "D1", false, "pwd123456")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists")
                .hasMessageContaining("bob");
        verify(userGateway, never()).save(any());
    }

    @Test
    @DisplayName("成功新增 → passwordHash 走 encoder.encode，status=ACTIVE，userKey 为 UUID")
    void successEncodesPassword() {
        when(userGateway.findByUsername(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("plain-secret")).thenReturn("$2a$10$HASHED");

        UserVO vo = exe.execute(cmd("alice", "D-1", true, "plain-secret"));

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userGateway, times(1)).save(cap.capture());
        User saved = cap.getValue();
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getDepartmentId()).isEqualTo("D-1");
        assertThat(saved.getAdmin()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(User.STATUS_ACTIVE);
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$10$HASHED").isNotEqualTo("plain-secret");
        assertThat(saved.getUserKey()).isNotBlank().hasSize(36);
        assertThat(vo.getUsername()).isEqualTo("alice");
        assertThat(vo.getAdmin()).isTrue();
    }

    @Test
    @DisplayName("admin null → 默认 false")
    void nullAdminDefaultsToFalse() {
        when(userGateway.findByUsername(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$X");

        exe.execute(cmd("carol", "D-1", null, "pwd123456"));

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userGateway).save(cap.capture());
        assertThat(cap.getValue().getAdmin()).isFalse();
    }

    @Test
    @DisplayName(
            "password 空/null → 不 encode、passwordHash 保持 null（TODO(行为可疑): 允许无密用户可能造成账号无法登录，建议强制非空）")
    void blankPasswordLeavesHashNull() {
        when(userGateway.findByUsername(anyString())).thenReturn(Optional.empty());

        exe.execute(cmd("dave", "D-1", false, "  "));

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userGateway).save(cap.capture());
        assertThat(cap.getValue().getPasswordHash()).isNull();
        verify(passwordEncoder, never()).encode(anyString());
    }
}
