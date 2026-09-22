package com.mouhin.knowledge.repository.application.executor.examreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 试卷作废命令执行器单测：锁定四种行为—— （1）正常作废：状态转 VOIDED、审计列回写、级联场次标 voided=true 并返回受影响数； （2）幂等：已是 VOIDED
 * 时不再改状态，仍触发级联（防止历史数据漂移）； （3）空 / 无效 sessionKey 抛非法参数，不落库； （4）操作人空白回退 "admin"。
 *
 * @author mouhinU
 * @date 2026-09-20
 */
@DisplayName("试卷作废命令执行器 (VoidPaperCmdExe)")
class VoidPaperCmdExeTest {

    private static final String SESSION = "sess-void-1";

    private final ExamHistoryGateway historyGateway = mock(ExamHistoryGateway.class);
    private final ExamSessionGateway sessionGateway = mock(ExamSessionGateway.class);
    private final VoidPaperCmdExe exe = new VoidPaperCmdExe(historyGateway, sessionGateway);

    @Test
    @DisplayName("PUBLISHED 试卷作废 → 状态转 VOIDED + 级联场次标 voided + 返回受影响数")
    void voidPublishedPaper() {
        ExamHistory history = new ExamHistory();
        history.setId(101L);
        history.setSessionId(SESSION);
        history.setStatus(ExamHistory.STATUS_PUBLISHED);
        when(historyGateway.findBySessionId(SESSION)).thenReturn(Optional.of(history));
        when(sessionGateway.markVoidedByExamHistoryId(eq(101L), eq(true))).thenReturn(3);

        int cascaded = exe.execute(SESSION, "teacher-A");

        assertEquals(3, cascaded, "应返回级联标注的场次数");
        ArgumentCaptor<ExamHistory> cap = ArgumentCaptor.forClass(ExamHistory.class);
        verify(historyGateway).update(cap.capture());
        ExamHistory saved = cap.getValue();
        assertEquals(ExamHistory.STATUS_VOIDED, saved.getStatus());
        assertTrue(saved.isVoided(), "isVoided() 应为 true");
        assertEquals("teacher-A", saved.getReviewedBy(), "作废人应记录在 reviewedBy");
        assertNotNull(saved.getReviewedTime(), "作废时间应记录在 reviewedTime");
        verify(sessionGateway).markVoidedByExamHistoryId(101L, true);
    }

    @Test
    @DisplayName("已 VOIDED 幂等：不改状态但级联仍触发，返回级联数")
    void voidAlreadyVoidedIsIdempotent() {
        ExamHistory history = new ExamHistory();
        history.setId(102L);
        history.setSessionId(SESSION);
        history.setStatus(ExamHistory.STATUS_VOIDED);
        history.setReviewedBy("first-op");
        when(historyGateway.findBySessionId(SESSION)).thenReturn(Optional.of(history));
        when(sessionGateway.markVoidedByExamHistoryId(eq(102L), eq(true))).thenReturn(5);

        int cascaded = exe.execute(SESSION, "second-op");

        assertEquals(5, cascaded);
        verify(historyGateway, never()).update(org.mockito.ArgumentMatchers.any());
        verify(sessionGateway).markVoidedByExamHistoryId(102L, true);
    }

    @Test
    @DisplayName("空 sessionId 抛非法参数，不落库不级联")
    void blankSessionRejected() {
        assertThrows(IllegalArgumentException.class, () -> exe.execute("  ", "admin"));
        verify(historyGateway, never()).update(org.mockito.ArgumentMatchers.any());
        verify(sessionGateway, never()).markVoidedByExamHistoryId(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("operator 空白 → 回退 'admin' 记入审计列")
    void blankOperatorDefaultsToAdmin() {
        ExamHistory history = new ExamHistory();
        history.setId(200L);
        history.setSessionId(SESSION);
        history.setStatus(ExamHistory.STATUS_REVIEWABLE);
        when(historyGateway.findBySessionId(SESSION)).thenReturn(Optional.of(history));
        when(sessionGateway.markVoidedByExamHistoryId(eq(200L), eq(true))).thenReturn(0);

        exe.execute(SESSION, "");

        ArgumentCaptor<ExamHistory> cap = ArgumentCaptor.forClass(ExamHistory.class);
        verify(historyGateway, times(1)).update(cap.capture());
        assertEquals("admin", cap.getValue().getReviewedBy());
    }
}
