package com.mouhin.knowledge.repository.application.executor.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.StudentVO;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 令牌校验查询执行器单测：锁定 4 条 Empty 短路 —— 空 token / 找不到 / 令牌过期 / 账号非 ACTIVE； 及 1 条成功路径返回 VO。
 *
 * @author mouhinU
 * @date 2026-09-24 16:32:00
 */
@DisplayName("考生令牌校验查询执行器 (StudentValidateTokenQryExe)")
class StudentValidateTokenQryExeTest {

    private final StudentGateway studentGateway = mock(StudentGateway.class);
    private final StudentValidateTokenQryExe exe = new StudentValidateTokenQryExe(studentGateway);

    private Student studentWithToken(String status, LocalDateTime expiry) {
        Student s = new Student();
        s.setId(7L);
        s.setUsername("alice");
        s.setStatus(status);
        s.setSessionToken("T1");
        s.setTokenExpiry(expiry);
        return s;
    }

    @Test
    @DisplayName("token null / 空白 → Optional.empty，不查库")
    void blankTokenShortCircuits() {
        assertThat(exe.execute(null)).isEmpty();
        assertThat(exe.execute("   ")).isEmpty();
        verify(studentGateway, never()).findBySessionToken(anyString());
    }

    @Test
    @DisplayName("token 不存在 → Optional.empty")
    void unknownTokenReturnsEmpty() {
        when(studentGateway.findBySessionToken("T1")).thenReturn(Optional.empty());
        assertThat(exe.execute("T1")).isEmpty();
    }

    @Test
    @DisplayName("令牌已过期 → Optional.empty（isTokenValid=false）")
    void expiredTokenReturnsEmpty() {
        Student s = studentWithToken("ACTIVE", LocalDateTime.now().minusMinutes(1));
        when(studentGateway.findBySessionToken("T1")).thenReturn(Optional.of(s));
        assertThat(exe.execute("T1")).isEmpty();
    }

    @Test
    @DisplayName("账号非 ACTIVE → Optional.empty，即使 token 仍在有效期内")
    void disabledAccountReturnsEmptyEvenIfTokenAlive() {
        Student s = studentWithToken("DISABLED", LocalDateTime.now().plusHours(1));
        when(studentGateway.findBySessionToken("T1")).thenReturn(Optional.of(s));
        assertThat(exe.execute("T1")).isEmpty();
    }

    @Test
    @DisplayName("有效令牌 + ACTIVE 账号 → 返回 StudentVO，含 username/displayName，不暴露 token/passwordHash")
    void happyPathReturnsVo() {
        Student s = studentWithToken("ACTIVE", LocalDateTime.now().plusHours(1));
        s.setDisplayName("Alice");
        s.setStudentNo("S001");
        s.setPasswordHash("$2a$10$abc");
        when(studentGateway.findBySessionToken("T1")).thenReturn(Optional.of(s));

        Optional<StudentVO> vo = exe.execute("T1");

        assertThat(vo).isPresent();
        assertThat(vo.get().getUsername()).isEqualTo("alice");
        assertThat(vo.get().getDisplayName()).isEqualTo("Alice");
        assertThat(vo.get().getStudentNo()).isEqualTo("S001");
    }

    @Test
    @DisplayName("displayName 为 null → VO 回退 username；studentNo null → 空串")
    void voFallsBackDisplayNameAndStudentNo() {
        Student s = studentWithToken("ACTIVE", LocalDateTime.now().plusHours(1));
        s.setDisplayName(null);
        s.setStudentNo(null);
        when(studentGateway.findBySessionToken("T1")).thenReturn(Optional.of(s));

        Optional<StudentVO> vo = exe.execute("T1");

        assertThat(vo).isPresent();
        assertThat(vo.get().getDisplayName()).isEqualTo("alice");
        assertThat(vo.get().getStudentNo()).isEmpty();
    }
}
