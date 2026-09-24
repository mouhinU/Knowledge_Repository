package com.mouhin.knowledge.repository.application.executor.examtaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 管理员覆盖考试时长执行器单测（ExamUpdateDurationCmdExe）。
 *
 * <p>锁定用例编排：正常覆盖时长回写 + 更新时间、场次不存在拒绝、空标识拒绝、时长置空表示不限时。
 *
 * <p>注：此为管理端覆盖入口，源码不含「本人 / 已交卷」门禁（越权与状态校验在更上层适配），本测试据实断言当前行为。
 *
 * @author mouhinU
 * @date 2026-09-24 17:27:38
 */
@DisplayName("覆盖考试时长执行器 (ExamUpdateDurationCmdExe)")
class ExamUpdateDurationCmdExeTest {

    private static final String SESSION_KEY = "sess-dur-1";

    private final ExamSessionGateway sessionGateway = mock(ExamSessionGateway.class);
    private final ExamUpdateDurationCmdExe exe = new ExamUpdateDurationCmdExe(sessionGateway);

    @Test
    @DisplayName("正常覆盖时长 → 回写 durationMinutes + updateTime 并落库")
    void updatesDuration() {
        ExamSession session = new ExamSession();
        session.setId(90L);
        session.setDurationMinutes(60);
        when(sessionGateway.findBySessionKey(SESSION_KEY)).thenReturn(Optional.of(session));

        exe.execute(SESSION_KEY, 90);

        ArgumentCaptor<ExamSession> cap = ArgumentCaptor.forClass(ExamSession.class);
        verify(sessionGateway).update(cap.capture());
        assertThat(cap.getValue().getDurationMinutes()).isEqualTo(90);
        assertThat(cap.getValue().getUpdateTime()).isNotNull();
    }

    @Test
    @DisplayName("场次不存在 → 抛'考试场次不存在'，不落库")
    void rejectsUnknownSession() {
        when(sessionGateway.findBySessionKey(SESSION_KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(SESSION_KEY, 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");
        verify(sessionGateway, never()).update(any());
    }

    @Test
    @DisplayName("空 sessionKey → 仓储查无此场 → 抛非法参数，不落库")
    void rejectsBlankSessionKey() {
        when(sessionGateway.findBySessionKey("  ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute("  ", 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");
        verify(sessionGateway, never()).update(any());
    }

    @Test
    @DisplayName("时长置 null → 视为不限时，照常回写 null")
    void allowsNullDurationForUnlimited() {
        ExamSession session = new ExamSession();
        session.setId(91L);
        session.setDurationMinutes(60);
        when(sessionGateway.findBySessionKey(SESSION_KEY)).thenReturn(Optional.of(session));

        exe.execute(SESSION_KEY, null);

        ArgumentCaptor<ExamSession> cap = ArgumentCaptor.forClass(ExamSession.class);
        verify(sessionGateway).update(cap.capture());
        assertThat(cap.getValue().getDurationMinutes()).isNull();
    }
}
