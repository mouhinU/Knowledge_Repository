package com.mouhin.knowledge.repository.application.executor.adminauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.AdminChangePasswordCmd;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 管理端自助改密单测：锁定 userKey 空 / newPassword 空 / 用户不存在 / 原密码错误 四条拒绝路径， 以及成功路径的 passwordHash 覆盖写入。特别断言新密码
 * hash 与明文不等，且旧 hash 被完全替换。
 *
 * @author mouhinU
 * @date 2026-09-24 16:36:00
 */
@DisplayName("管理端自助改密命令执行器 (AdminChangePasswordCmdExe)")
class AdminChangePasswordCmdExeTest {

    private final UserGateway userGateway = mock(UserGateway.class);
    private final BCryptPasswordEncoder passwordEncoder = mock(BCryptPasswordEncoder.class);
    private final AdminChangePasswordCmdExe exe =
            new AdminChangePasswordCmdExe(userGateway, passwordEncoder);

    private AdminChangePasswordCmd cmd(String userKey, String oldPwd, String newPwd) {
        AdminChangePasswordCmd c = new AdminChangePasswordCmd();
        c.setUserKey(userKey);
        c.setOldPassword(oldPwd);
        c.setNewPassword(newPwd);
        return c;
    }

    private User existingUser() {
        User u = new User();
        u.setUserKey("k1");
        u.setUsername("admin");
        u.setPasswordHash("$2a$10$OLD");
        u.setStatus(User.STATUS_ACTIVE);
        return u;
    }

    @Test
    @DisplayName("userKey 空 → '未认证的改密请求'，不查库")
    void blankUserKeyRejected() {
        assertThatThrownBy(() -> exe.execute(cmd(null, "old", "newpwd")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("未认证的改密请求");
        assertThatThrownBy(() -> exe.execute(cmd("  ", "old", "newpwd")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("未认证的改密请求");
        verify(userGateway, never()).findByUserKey(anyString());
    }

    @Test
    @DisplayName("newPassword 空 → '新密码不能为空'")
    void blankNewPasswordRejected() {
        assertThatThrownBy(() -> exe.execute(cmd("k1", "old", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("新密码不能为空");
        assertThatThrownBy(() -> exe.execute(cmd("k1", "old", "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("新密码不能为空");
        verify(userGateway, never()).update(any());
    }

    @Test
    @DisplayName("userKey 找不到用户 → '用户不存在'")
    void userNotFoundRejected() {
        when(userGateway.findByUserKey("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(cmd("ghost", "old", "newpwd")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户不存在");
    }

    @Test
    @DisplayName("原密码不匹配 → '原密码错误'，不 update")
    void wrongOldPasswordRejected() {
        User u = existingUser();
        when(userGateway.findByUserKey("k1")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("wrong", "$2a$10$OLD")).thenReturn(false);

        assertThatThrownBy(() -> exe.execute(cmd("k1", "wrong", "newpwd")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("原密码错误");
        verify(userGateway, never()).update(any());
    }

    @Test
    @DisplayName("成功改密 → passwordHash 被 encoder.encode(newPwd) 覆盖，update 调用一次")
    void successOverwritesHashAndPersists() {
        User u = existingUser();
        when(userGateway.findByUserKey("k1")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("oldpw", "$2a$10$OLD")).thenReturn(true);
        when(passwordEncoder.encode("newpw")).thenReturn("$2a$10$NEW");

        exe.execute(cmd("k1", "oldpw", "newpw"));

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userGateway, times(1)).update(cap.capture());
        User saved = cap.getValue();
        assertThat(saved.getPasswordHash())
                .isEqualTo("$2a$10$NEW")
                .isNotEqualTo("$2a$10$OLD")
                .isNotEqualTo("newpw")
                .doesNotContain("newpw");
    }
}
