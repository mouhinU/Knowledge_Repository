package com.mouhin.knowledge.repository.application.executor.student;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 考生退出登录命令执行器单测：核心断言 —— 令牌存在时走专用 {@code clearSessionToken(id)}， 而非通用 update；令牌缺失静默幂等。
 *
 * @author mouhinU
 * @date 2026-09-24 16:33:00
 */
@DisplayName("考生退出登录命令执行器 (StudentLogoutCmdExe)")
class StudentLogoutCmdExeTest {

    private final StudentGateway studentGateway = mock(StudentGateway.class);
    private final StudentLogoutCmdExe exe = new StudentLogoutCmdExe(studentGateway);

    @Test
    @DisplayName("令牌命中学生 → 走 clearSessionToken 显式置空（不用 update），一次调用")
    void tokenHitInvokesClearSessionToken() {
        Student s = new Student();
        s.setId(42L);
        s.setSessionToken("T1");
        s.setTokenExpiry(LocalDateTime.now().plusHours(1));
        when(studentGateway.findBySessionToken("T1")).thenReturn(Optional.of(s));

        exe.execute("T1");

        verify(studentGateway, times(1)).clearSessionToken(42L);
        verify(studentGateway, never()).update(any());
    }

    @Test
    @DisplayName("令牌未命中 → 静默幂等，无库操作")
    void unknownTokenIsSilentlyIdempotent() {
        when(studentGateway.findBySessionToken("nope")).thenReturn(Optional.empty());

        assertThatCode(() -> exe.execute("nope")).doesNotThrowAnyException();
        verify(studentGateway, never()).clearSessionToken(anyLong());
        verify(studentGateway, never()).update(any());
    }

    @Test
    @DisplayName("token null / 空白 → 交由 gateway 判空返回 empty，无副作用")
    void blankTokenIsSafe() {
        when(studentGateway.findBySessionToken(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> exe.execute(null)).doesNotThrowAnyException();
        assertThatCode(() -> exe.execute("")).doesNotThrowAnyException();
        verify(studentGateway, never()).clearSessionToken(anyLong());
    }
}
