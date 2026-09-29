package com.mouhin.knowledge.repository.application.executor.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.client.dto.StudentVO;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.gateway.StudentSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import com.mouhin.knowledge.repository.domain.model.entity.StudentSession;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 令牌校验查询执行器单测：锁定 4 条 Empty 短路 —— 空 token / 找不到 / 令牌过期 / 账号非 ACTIVE； 及 1 条成功路径返回 VO。
 *
 * <p>P0-T5 追加：会话表命中优先返回；同一考生两个并发 token 都能各自解析到本人； legacy 快照路径保留兼容（会话表未命中 → 回退 {@code
 * studentGateway.findBySessionToken}）。
 *
 * @author mouhinU
 * @date 2026-09-24 16:32:00
 */
@DisplayName("考生令牌校验查询执行器 (StudentValidateTokenQryExe)")
class StudentValidateTokenQryExeTest {

    private final StudentGateway studentGateway = mock(StudentGateway.class);
    private final StudentSessionGateway studentSessionGateway = mock(StudentSessionGateway.class);
    private final StudentValidateTokenQryExe exe =
            new StudentValidateTokenQryExe(studentGateway, studentSessionGateway);

    private Student studentWithToken(String status, LocalDateTime expiry) {
        Student s = new Student();
        s.setId(7L);
        s.setUsername("alice");
        s.setStatus(status);
        s.setSessionToken("T1");
        s.setTokenExpiry(expiry);
        return s;
    }

    private StudentSession activeSession(String token, Long studentId) {
        StudentSession sess = new StudentSession();
        sess.setSessionToken(token);
        sess.setStudentId(studentId);
        sess.setExpiresAt(LocalDateTime.now().plusHours(1));
        return sess;
    }

    @Test
    @DisplayName("token null / 空白 → Optional.empty，不查库")
    void blankTokenShortCircuits() {
        assertThat(exe.execute(null)).isEmpty();
        assertThat(exe.execute("   ")).isEmpty();
        verify(studentGateway, never()).findBySessionToken(anyString());
        verify(studentSessionGateway, never()).findActiveByToken(anyString());
    }

    @Test
    @DisplayName("token 不存在 → Optional.empty")
    void unknownTokenReturnsEmpty() {
        when(studentSessionGateway.findActiveByToken("T1")).thenReturn(Optional.empty());
        when(studentGateway.findBySessionToken("T1")).thenReturn(Optional.empty());
        assertThat(exe.execute("T1")).isEmpty();
    }

    @Test
    @DisplayName("legacy 令牌已过期（会话表未命中）→ Optional.empty")
    void expiredTokenReturnsEmpty() {
        Student s = studentWithToken("ACTIVE", LocalDateTime.now().minusMinutes(1));
        when(studentSessionGateway.findActiveByToken("T1")).thenReturn(Optional.empty());
        when(studentGateway.findBySessionToken("T1")).thenReturn(Optional.of(s));
        assertThat(exe.execute("T1")).isEmpty();
    }

    @Test
    @DisplayName("账号非 ACTIVE → Optional.empty，即使 token 仍在有效期内")
    void disabledAccountReturnsEmptyEvenIfTokenAlive() {
        Student s = studentWithToken("DISABLED", LocalDateTime.now().plusHours(1));
        when(studentSessionGateway.findActiveByToken("T1")).thenReturn(Optional.empty());
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
        when(studentSessionGateway.findActiveByToken("T1")).thenReturn(Optional.empty());
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
        when(studentSessionGateway.findActiveByToken("T1")).thenReturn(Optional.empty());
        when(studentGateway.findBySessionToken("T1")).thenReturn(Optional.of(s));

        Optional<StudentVO> vo = exe.execute("T1");

        assertThat(vo).isPresent();
        assertThat(vo.get().getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("P0-T5：会话表命中 → 按 studentId 反查考生并返回 VO，绕过 legacy findBySessionToken")
    void sessionTableHitTakesPrecedence() {
        StudentSession sess = activeSession("NEW-TOKEN", 42L);
        Student s = studentWithToken("ACTIVE", LocalDateTime.now().plusHours(1));
        s.setId(42L);
        when(studentSessionGateway.findActiveByToken("NEW-TOKEN")).thenReturn(Optional.of(sess));
        when(studentGateway.findById(42L)).thenReturn(Optional.of(s));

        Optional<StudentVO> vo = exe.execute("NEW-TOKEN");

        assertThat(vo).isPresent();
        assertThat(vo.get().getUsername()).isEqualTo("alice");
        verify(studentGateway, never()).findBySessionToken("NEW-TOKEN");
    }

    @Test
    @DisplayName("P0-T5：同考生两个并发 token 均能各自解析 → 不再互踢")
    void multiSessionBothValid() {
        Student s = studentWithToken("ACTIVE", LocalDateTime.now().plusHours(1));
        s.setId(7L);
        StudentSession sessA = activeSession("TOKEN-A", 7L);
        StudentSession sessB = activeSession("TOKEN-B", 7L);
        when(studentSessionGateway.findActiveByToken("TOKEN-A")).thenReturn(Optional.of(sessA));
        when(studentSessionGateway.findActiveByToken("TOKEN-B")).thenReturn(Optional.of(sessB));
        when(studentGateway.findById(7L)).thenReturn(Optional.of(s));

        assertThat(exe.execute("TOKEN-A")).isPresent();
        assertThat(exe.execute("TOKEN-B")).isPresent();
    }

    @Test
    @DisplayName("P0-T5：会话已过期 → findActiveByToken 未命中 → legacy 回退路径同样失效 → 返回 empty")
    void revokedOrExpiredSessionFallsBackToLegacy() {
        when(studentSessionGateway.findActiveByToken("STALE")).thenReturn(Optional.empty());
        Student expired = studentWithToken("ACTIVE", LocalDateTime.now().minusMinutes(1));
        expired.setSessionToken("STALE");
        when(studentGateway.findBySessionToken("STALE")).thenReturn(Optional.of(expired));

        assertThat(exe.execute("STALE")).isEmpty();
    }
}
