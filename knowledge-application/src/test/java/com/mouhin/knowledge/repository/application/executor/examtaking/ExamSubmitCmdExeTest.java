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
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 交卷执行器单测（ExamSubmitCmdExe）。
 *
 * <p>锁定交卷编排：正常交卷改状态 SUBMITTED + 记录交卷时间、重复交卷拒绝、超时仍允许交卷（仅记日志）、 越权访问拒绝。评分与交卷已解耦，故本执行器不触发评分事件。
 *
 * @author mouhinU
 * @date 2026-09-24 17:27:38
 */
@DisplayName("交卷执行器 (ExamSubmitCmdExe)")
class ExamSubmitCmdExeTest {

    private static final String SESSION_KEY = "sess-submit-1";
    private static final String TOKEN = "stu-token";

    private final ExamSessionGateway sessionGateway = mock(ExamSessionGateway.class);
    private final ExamTakingSupport support = mock(ExamTakingSupport.class);
    private final ExamSubmitCmdExe exe = new ExamSubmitCmdExe(sessionGateway, support);

    private ExamSession inProgress() {
        ExamSession session = new ExamSession();
        session.setId(80L);
        session.setStudentId(7L);
        session.setStatus("IN_PROGRESS");
        session.setStartTime(LocalDateTime.now().minusMinutes(5));
        session.setDurationMinutes(60);
        when(support.resolveSession(SESSION_KEY, TOKEN)).thenReturn(session);
        return session;
    }

    @Test
    @DisplayName("正常交卷 → 状态转 SUBMITTED + 记录交卷时间并落库")
    void submitsInProgressSession() {
        inProgress();

        exe.execute(SESSION_KEY, TOKEN);

        ArgumentCaptor<ExamSession> cap = ArgumentCaptor.forClass(ExamSession.class);
        verify(sessionGateway).update(cap.capture());
        ExamSession saved = cap.getValue();
        assertThat(saved.getStatus()).isEqualTo("SUBMITTED");
        assertThat(saved.getSubmitTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
    }

    @Test
    @DisplayName("重复交卷（已 SUBMITTED）→ 抛'请勿重复操作'，不再落库")
    void rejectsDoubleSubmit() {
        ExamSession session = new ExamSession();
        session.setId(81L);
        session.setStatus("SUBMITTED");
        when(support.resolveSession(SESSION_KEY, TOKEN)).thenReturn(session);

        assertThatThrownBy(() -> exe.execute(SESSION_KEY, TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复");
        verify(sessionGateway, never()).update(any());
    }

    @Test
    @DisplayName("超过考试时长仍允许交卷（仅记 WARN 日志），状态照常转 SUBMITTED")
    void allowsLateSubmitBeyondDuration() {
        ExamSession session = inProgress();
        session.setDurationMinutes(1);
        session.setStartTime(LocalDateTime.now().minusHours(3));

        exe.execute(SESSION_KEY, TOKEN);

        ArgumentCaptor<ExamSession> cap = ArgumentCaptor.forClass(ExamSession.class);
        verify(sessionGateway).update(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("越权访问他人场次 → resolveSession 抛'无权访问'，不改状态")
    void rejectsForeignSession() {
        when(support.resolveSession(SESSION_KEY, TOKEN))
                .thenThrow(new IllegalArgumentException("无权访问此考试"));

        assertThatThrownBy(() -> exe.execute(SESSION_KEY, TOKEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无权");
        verify(sessionGateway, never()).update(any());
    }

    @Test
    @DisplayName("无作答也允许交卷 → 状态照常 SUBMITTED")
    void allowsSubmitWithNoAnswers() {
        ExamSession session = inProgress();
        session.setDurationMinutes(null);
        session.setStartTime(null);

        exe.execute(SESSION_KEY, TOKEN);

        ArgumentCaptor<ExamSession> cap = ArgumentCaptor.forClass(ExamSession.class);
        verify(sessionGateway).update(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("SUBMITTED");
    }
}
