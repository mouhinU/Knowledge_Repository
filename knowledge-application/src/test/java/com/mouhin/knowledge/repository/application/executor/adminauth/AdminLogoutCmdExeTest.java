package com.mouhin.knowledge.repository.application.executor.adminauth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.AdminJwtService;
import com.mouhin.knowledge.repository.domain.model.valueobject.AdminTokenPayload;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 管理端退出登录命令执行器单测：JWT 无状态，服务端不做黑名单—— 只 verify + 审计日志。断言：verify 被调用、结果无论有无均不抛异常、 无用户表写操作。
 *
 * @author mouhinU
 * @date 2026-09-24 16:35:00
 */
@DisplayName("管理端退出登录命令执行器 (AdminLogoutCmdExe)")
class AdminLogoutCmdExeTest {

    private final AdminJwtService adminJwtService = mock(AdminJwtService.class);
    private final AdminLogoutCmdExe exe = new AdminLogoutCmdExe(adminJwtService);

    @Test
    @DisplayName("合法令牌 → verify 被调用，无异常抛出（仅审计日志）")
    void validTokenIsVerifiedAndNoThrow() {
        when(adminJwtService.verify("good"))
                .thenReturn(
                        Optional.of(
                                new AdminTokenPayload(
                                        "k1", "admin", true, Instant.now().plusSeconds(60))));

        assertThatCode(() -> exe.execute("good")).doesNotThrowAnyException();
        verify(adminJwtService, times(1)).verify("good");
    }

    @Test
    @DisplayName("非法 / 过期令牌 → 静默幂等，不抛（前端已丢本地令牌，后端不敏感）")
    void invalidTokenIsSilentlyIdempotent() {
        when(adminJwtService.verify(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> exe.execute("bad")).doesNotThrowAnyException();
        verify(adminJwtService, times(1)).verify("bad");
    }

    @Test
    @DisplayName("null token → 直接传给 verify，不 NPE")
    void nullTokenIsSafe() {
        when(adminJwtService.verify(null)).thenReturn(Optional.empty());

        assertThatCode(() -> exe.execute(null)).doesNotThrowAnyException();
        verify(adminJwtService, never())
                .issue(anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
