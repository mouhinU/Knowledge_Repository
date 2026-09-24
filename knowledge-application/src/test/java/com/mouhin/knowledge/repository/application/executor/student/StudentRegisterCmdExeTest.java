package com.mouhin.knowledge.repository.application.executor.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.StudentRegisterCmd;
import com.mouhin.knowledge.repository.client.dto.StudentVO;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 考生注册命令执行器单测：锁定入参校验 / 唯一性 / 密码 hash 落库 / displayName 回退 四条主干分支。 特别断言 {@code passwordHash !=
 * 明文}，防止误把明文写库。
 *
 * @author mouhinU
 * @date 2026-09-24 16:31:00
 */
@DisplayName("考生注册命令执行器 (StudentRegisterCmdExe)")
class StudentRegisterCmdExeTest {

    private final StudentGateway studentGateway = mock(StudentGateway.class);
    private final BCryptPasswordEncoder passwordEncoder = mock(BCryptPasswordEncoder.class);
    private final StudentRegisterCmdExe exe =
            new StudentRegisterCmdExe(studentGateway, passwordEncoder);

    private StudentRegisterCmd cmd(String user, String pwd, String display, String no) {
        StudentRegisterCmd c = new StudentRegisterCmd();
        c.setUsername(user);
        c.setPassword(pwd);
        c.setDisplayName(display);
        c.setStudentNo(no);
        return c;
    }

    @Test
    @DisplayName("用户名为空 / null → 拒绝，不落库")
    void blankUsernameRejected() {
        assertThatThrownBy(() -> exe.execute(cmd(null, "pwd123", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户名不能为空");
        assertThatThrownBy(() -> exe.execute(cmd("   ", "pwd123", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户名不能为空");
        verify(studentGateway, never()).save(any());
    }

    @Test
    @DisplayName("密码长度 <6 → 拒绝")
    void shortPasswordRejected() {
        assertThatThrownBy(() -> exe.execute(cmd("alice", "abc12", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("密码长度至少 6 位");
        assertThatThrownBy(() -> exe.execute(cmd("alice", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密码");
        verify(studentGateway, never()).save(any());
    }

    @Test
    @DisplayName("用户名已存在 → 拒绝并携带原用户名（消息含 '已存在'）")
    void duplicateUsernameRejected() {
        when(studentGateway.findByUsername("alice")).thenReturn(Optional.of(new Student()));

        assertThatThrownBy(() -> exe.execute(cmd("alice", "pwd123", "Alice", "S001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已存在")
                .hasMessageContaining("alice");
        verify(studentGateway, never()).save(any());
    }

    @Test
    @DisplayName("成功注册 → passwordHash 走 encoder.encode，status=ACTIVE，VO 不回显敏感字段")
    void successEncodesPasswordAndPersists() {
        when(studentGateway.findByUsername("alice")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pwd123")).thenReturn("$2a$10$HASHED");

        StudentVO vo = exe.execute(cmd("alice", "pwd123", "Alice Zhang", "S001"));

        ArgumentCaptor<Student> cap = ArgumentCaptor.forClass(Student.class);
        verify(studentGateway).save(cap.capture());
        Student saved = cap.getValue();
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getPasswordHash())
                .isEqualTo("$2a$10$HASHED")
                .isNotEqualTo("pwd123")
                .doesNotContain("pwd123");
        assertThat(saved.getDisplayName()).isEqualTo("Alice Zhang");
        assertThat(saved.getStudentNo()).isEqualTo("S001");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
        // VO 应仅暴露安全字段
        assertThat(vo.getUsername()).isEqualTo("alice");
        assertThat(vo.getDisplayName()).isEqualTo("Alice Zhang");
    }

    @Test
    @DisplayName("displayName 为 null → 回退为 username")
    void displayNameFallsBackToUsername() {
        when(studentGateway.findByUsername("bob")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$X");

        StudentVO vo = exe.execute(cmd("bob", "pwd123456", null, null));

        ArgumentCaptor<Student> cap = ArgumentCaptor.forClass(Student.class);
        verify(studentGateway).save(cap.capture());
        assertThat(cap.getValue().getDisplayName()).isEqualTo("bob");
        assertThat(vo.getDisplayName()).isEqualTo("bob");
    }
}
