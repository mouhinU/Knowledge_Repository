package com.mouhin.knowledge.repository.application.executor.student;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.gateway.StudentSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import com.mouhin.knowledge.repository.domain.model.entity.StudentSession;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 考生退出登录命令执行器单测：核心断言 —— 令牌存在时同时撤销会话表 + 清空 legacy 快照； 令牌缺失静默幂等；P0-T5 追加：会话表命中即调 revoke(token)，不依赖
 * legacy 命中。
 *
 * @author mouhinU
 * @date 2026-09-24 16:33:00
 */
@DisplayName("考生退出登录命令执行器 (StudentLogoutCmdExe)")
class StudentLogoutCmdExeTest {

    private final StudentGateway studentGateway = mock(StudentGateway.class);
    private final StudentSessionGateway studentSessionGateway = mock(StudentSessionGateway.class);
    private final StudentLogoutCmdExe exe =
            new StudentLogoutCmdExe(studentGateway, studentSessionGateway);

    @Test
    @DisplayName("legacy 令牌命中 → clearSessionToken + revoke(会话表)，一次调用")
    void tokenHitInvokesClearSessionToken() {
        Student s = new Student();
        s.setId(42L);
        s.setSessionToken("T1");
        s.setTokenExpiry(LocalDateTime.now().plusHours(1));
        StudentSession sess = new StudentSession();
        sess.setId(100L);
        sess.setSessionToken("T1");
        sess.setStudentId(42L);
        when(studentSessionGateway.findByToken("T1")).thenReturn(Optional.of(sess));
        when(studentGateway.findBySessionToken("T1")).thenReturn(Optional.of(s));

        exe.execute("T1");

        verify(studentSessionGateway, times(1)).revoke("T1");
        verify(studentGateway, times(1)).clearSessionToken(42L);
        verify(studentGateway, never()).update(any());
    }

    @Test
    @DisplayName("P0-T5：仅会话表命中（历史多端并发登录 token 已非 legacy 快照）→ 只 revoke，不 clearSessionToken")
    void onlySessionTableHit() {
        StudentSession sess = new StudentSession();
        sess.setId(200L);
        sess.setSessionToken("T-CONCURRENT");
        sess.setStudentId(9L);
        when(studentSessionGateway.findByToken("T-CONCURRENT")).thenReturn(Optional.of(sess));
        // legacy 快照已被后续登录覆盖，找不到本 token
        when(studentGateway.findBySessionToken("T-CONCURRENT")).thenReturn(Optional.empty());

        exe.execute("T-CONCURRENT");

        verify(studentSessionGateway, times(1)).revoke("T-CONCURRENT");
        verify(studentGateway, never()).clearSessionToken(anyLong());
    }

    @Test
    @DisplayName("令牌未命中 → 静默幂等，无库操作")
    void unknownTokenIsSilentlyIdempotent() {
        when(studentSessionGateway.findByToken("nope")).thenReturn(Optional.empty());
        when(studentGateway.findBySessionToken("nope")).thenReturn(Optional.empty());

        assertThatCode(() -> exe.execute("nope")).doesNotThrowAnyException();
        verify(studentGateway, never()).clearSessionToken(anyLong());
        verify(studentGateway, never()).update(any());
        verify(studentSessionGateway, never()).revoke(anyString());
    }

    @Test
    @DisplayName("token null / 空白 → 早退，无副作用")
    void blankTokenIsSafe() {
        assertThatCode(() -> exe.execute(null)).doesNotThrowAnyException();
        assertThatCode(() -> exe.execute("")).doesNotThrowAnyException();
        assertThatCode(() -> exe.execute("   ")).doesNotThrowAnyException();
        verify(studentGateway, never()).clearSessionToken(anyLong());
        verify(studentSessionGateway, never()).revoke(anyString());
        verify(studentSessionGateway, never()).findByToken(anyString());
    }
}
