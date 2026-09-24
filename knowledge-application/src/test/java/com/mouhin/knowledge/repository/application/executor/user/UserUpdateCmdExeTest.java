package com.mouhin.knowledge.repository.application.executor.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.UserUpdateCmd;
import com.mouhin.knowledge.repository.client.dto.UserVO;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 修改用户命令执行器单测：锁定"null 保持原值"的语义、非法 status 拒绝、 password 空串保持原 hash（不 encode）、大小写归一化，以及用户不存在的错误传播。
 *
 * @author mouhinU
 * @date 2026-09-24 16:38:00
 */
@DisplayName("修改用户命令执行器 (UserUpdateCmdExe)")
class UserUpdateCmdExeTest {

    private final UserGateway userGateway = mock(UserGateway.class);
    private final BCryptPasswordEncoder passwordEncoder = mock(BCryptPasswordEncoder.class);
    private final UserUpdateCmdExe exe = new UserUpdateCmdExe(userGateway, passwordEncoder);

    private User existing() {
        User u = new User();
        u.setUserKey("k1");
        u.setUsername("alice");
        u.setDepartmentId("D-1");
        u.setAdmin(false);
        u.setStatus(User.STATUS_ACTIVE);
        u.setPasswordHash("$2a$10$OLD");
        return u;
    }

    private UserUpdateCmd cmd(
            String userKey,
            String username,
            String dept,
            Boolean admin,
            String status,
            String pwd) {
        UserUpdateCmd c = new UserUpdateCmd();
        c.setUserKey(userKey);
        c.setUsername(username);
        c.setDepartmentId(dept);
        c.setAdmin(admin);
        c.setStatus(status);
        c.setPassword(pwd);
        return c;
    }

    @Test
    @DisplayName("userKey 不存在 → 'User not found'，不落 update")
    void userNotFoundRejected() {
        when(userGateway.findByUserKey("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(cmd("ghost", null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
        verify(userGateway, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("全字段 null → 原对象不变，仍触发一次 update（沿用既有语义）")
    void allNullKeepsOriginalFields() {
        User u = existing();
        when(userGateway.findByUserKey("k1")).thenReturn(Optional.of(u));

        UserVO vo = exe.execute(cmd("k1", null, null, null, null, null));

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userGateway, times(1)).update(cap.capture());
        User saved = cap.getValue();
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getDepartmentId()).isEqualTo("D-1");
        assertThat(saved.getAdmin()).isFalse();
        assertThat(saved.getStatus()).isEqualTo(User.STATUS_ACTIVE);
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$10$OLD");
        assertThat(vo.getUserKey()).isEqualTo("k1");
    }

    @Test
    @DisplayName("非法 status → 拒绝并携带原值，update 不调用")
    void invalidStatusRejected() {
        when(userGateway.findByUserKey("k1")).thenReturn(Optional.of(existing()));

        assertThatThrownBy(() -> exe.execute(cmd("k1", null, null, null, "PENDING", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid status");
        verify(userGateway, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("status 小写 active/disabled → 归一为大写落库")
    void statusCaseNormalized() {
        when(userGateway.findByUserKey("k1")).thenReturn(Optional.of(existing()));

        exe.execute(cmd("k1", null, null, null, "disabled", null));

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userGateway).update(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(User.STATUS_DISABLED);
    }

    @Test
    @DisplayName("password 非空 → 走 encoder.encode 覆盖 hash；空串/null 保持原 hash")
    void passwordOnlyReencodedWhenNonBlank() {
        when(userGateway.findByUserKey("k1")).thenReturn(Optional.of(existing()));
        when(passwordEncoder.encode("new-secret")).thenReturn("$2a$10$NEW");

        exe.execute(cmd("k1", null, null, null, null, "new-secret"));

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userGateway).update(cap.capture());
        assertThat(cap.getValue().getPasswordHash())
                .isEqualTo("$2a$10$NEW")
                .isNotEqualTo("new-secret");
    }

    @Test
    @DisplayName("password 空串 → 保持原 hash，encoder 未被调用")
    void blankPasswordKeepsHash() {
        when(userGateway.findByUserKey("k1")).thenReturn(Optional.of(existing()));

        exe.execute(cmd("k1", null, null, null, null, "  "));

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userGateway).update(cap.capture());
        assertThat(cap.getValue().getPasswordHash()).isEqualTo("$2a$10$OLD");
        verify(passwordEncoder, never()).encode(anyString());
    }
}
