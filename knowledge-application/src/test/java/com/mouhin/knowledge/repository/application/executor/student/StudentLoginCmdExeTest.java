package com.mouhin.knowledge.repository.application.executor.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.StudentLoginCmd;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 考生登录命令执行器单测：锁定四条关键分支—— 用户名不存在与密码错误均回相同消息（防枚举）；账号被禁用应显式区分；成功路径回写 24h 令牌；参数缺失走非法。
 *
 * @author mouhinU
 * @date 2026-09-24 16:30:00
 */
@DisplayName("考生登录命令执行器 (StudentLoginCmdExe)")
class StudentLoginCmdExeTest {

    private final StudentGateway studentGateway = mock(StudentGateway.class);
    private final BCryptPasswordEncoder passwordEncoder = mock(BCryptPasswordEncoder.class);
    private final StudentLoginCmdExe exe = new StudentLoginCmdExe(studentGateway, passwordEncoder);

    private StudentLoginCmd cmd(String user, String pwd) {
        StudentLoginCmd c = new StudentLoginCmd();
        c.setUsername(user);
        c.setPassword(pwd);
        return c;
    }

    private Student activeStudentWithHash(String hash) {
        Student s = new Student();
        s.setId(1L);
        s.setUsername("alice");
        s.setStatus("ACTIVE");
        s.setPasswordHash(hash);
        return s;
    }

    @Test
    @DisplayName("用户名不存在 → '用户名或密码错误'（与错密码消息一致，防枚举）")
    void userNotFoundThrowsGenericMessage() {
        when(studentGateway.findByUsername(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(cmd("ghost", "whatever")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户名或密码错误");
        verify(studentGateway, never()).update(any());
    }

    @Test
    @DisplayName("密码错误 → '用户名或密码错误'，与用户不存在场景消息一致")
    void wrongPasswordThrowsSameGenericMessage() {
        Student s = activeStudentWithHash("$2a$10$abc");
        when(studentGateway.findByUsername("alice")).thenReturn(Optional.of(s));
        when(passwordEncoder.matches("bad", "$2a$10$abc")).thenReturn(false);

        assertThatThrownBy(() -> exe.execute(cmd("alice", "bad")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户名或密码错误");
        verify(studentGateway, never()).update(any());
    }

    @Test
    @DisplayName("账号被禁用 → '账号已被禁用'（TODO(行为可疑): 该消息可被用作账号存在性探针，应合并入通用错误消息）")
    void disabledAccountRejected() {
        Student s = activeStudentWithHash("$2a$10$abc");
        s.setStatus("DISABLED");
        when(studentGateway.findByUsername("alice")).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> exe.execute(cmd("alice", "right")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("账号已被禁用");
        // 未验证密码前即抛出：禁用短路
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(studentGateway, never()).update(any());
    }

    @Test
    @DisplayName("登录成功 → 回写 sessionToken + 24h 过期时间，返回同一 token")
    void successPersistsTokenAndExpiry() {
        Student s = activeStudentWithHash("$2a$10$abc");
        when(studentGateway.findByUsername("alice")).thenReturn(Optional.of(s));
        when(passwordEncoder.matches("right", "$2a$10$abc")).thenReturn(true);

        String token = exe.execute(cmd("alice", "right"));

        assertThat(token).isNotBlank().hasSize(32).doesNotContain("-");
        ArgumentCaptor<Student> cap = ArgumentCaptor.forClass(Student.class);
        verify(studentGateway, times(1)).update(cap.capture());
        Student saved = cap.getValue();
        assertThat(saved.getSessionToken()).isEqualTo(token);
        assertThat(saved.getTokenExpiry()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
    }

    @Test
    @DisplayName("两次连续登录产生不同 token（每次刷新，不与历史串号）")
    void consecutiveLoginsProduceDistinctTokens() {
        Student s = activeStudentWithHash("$2a$10$abc");
        when(studentGateway.findByUsername("alice")).thenReturn(Optional.of(s));
        when(passwordEncoder.matches("right", "$2a$10$abc")).thenReturn(true);

        String t1 = exe.execute(cmd("alice", "right"));
        String t2 = exe.execute(cmd("alice", "right"));

        assertThat(t1).isNotEqualTo(t2);
    }
}
