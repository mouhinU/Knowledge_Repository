package com.mouhin.knowledge.repository.application.executor.examtaking;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 开考守卫单测：锁定「已作废不可开考」与「一人一卷一次」两条系统级门禁。
 *
 * <p>本测试仅覆盖新增门禁分支；正常开考路径依赖 renderWithPlan / injectImagesIntoSnapshot 等协作组件，不在此测试范围内。
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
@DisplayName("开考守卫：作废门禁 + 一人一卷一次 (ExamStartFromHistoryCmdExe)")
class ExamStartFromHistoryGuardTest {

    private static final String TOKEN = "stu-token";
    private static final String SESSION_ID = "sess-1";

    private final ExamHistoryGateway historyGateway = mock(ExamHistoryGateway.class);
    private final ExamSessionGateway sessionGateway = mock(ExamSessionGateway.class);
    private final ExamTakingSupport support = mock(ExamTakingSupport.class);
    private final ExamStartFromHistoryCmdExe exe =
            new ExamStartFromHistoryCmdExe(historyGateway, sessionGateway, support);

    private Student stubStudent() {
        Student student = new Student();
        student.setId(9L);
        when(support.resolveStudent(TOKEN)).thenReturn(student);
        return student;
    }

    private void stubHistory(ExamHistory history) {
        when(historyGateway.findBySessionId(SESSION_ID)).thenReturn(Optional.of(history));
    }

    @Test
    @DisplayName("试卷已作废 (VOIDED) → 抛 IllegalStateException，不落任何场次")
    void rejectsVoidedPaper() {
        stubStudent();
        ExamHistory history = new ExamHistory();
        history.setId(50L);
        history.setSessionId(SESSION_ID);
        history.setExamPaper("# 一份卷\n");
        history.setStatus(ExamHistory.STATUS_VOIDED);
        stubHistory(history);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> exe.execute(TOKEN, SESSION_ID));
        assertTrue(ex.getMessage().contains("作废"), "异常信息应包含'作废'关键字，实际：" + ex.getMessage());
        verify(sessionGateway, never()).save(any());
    }

    @Test
    @DisplayName("同考生已存在任意状态场次 → 抛'仅允许考试一次'异常，不落新场次")
    void rejectsSecondAttemptBySameStudent() {
        Student student = stubStudent();
        ExamHistory history = new ExamHistory();
        history.setId(60L);
        history.setSessionId(SESSION_ID);
        history.setExamPaper("# 一份卷\n");
        history.markPublished("admin");
        stubHistory(history);
        when(sessionGateway.existsByStudentIdAndExamHistoryId(eq(student.getId()), eq(60L)))
                .thenReturn(true);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> exe.execute(TOKEN, SESSION_ID));
        assertTrue(ex.getMessage().contains("一次"), "异常信息应含'一次'关键字，实际：" + ex.getMessage());
        verify(sessionGateway, never()).save(any());
    }

    @Test
    @DisplayName("试卷未发布 (REVIEWABLE) → 抛'尚未发布'异常")
    void rejectsUnpublishedPaper() {
        stubStudent();
        ExamHistory history = new ExamHistory();
        history.setId(70L);
        history.setSessionId(SESSION_ID);
        history.setExamPaper("# 一份卷\n");
        history.markReviewable();
        stubHistory(history);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> exe.execute(TOKEN, SESSION_ID));
        assertTrue(ex.getMessage().contains("发布"), "实际：" + ex.getMessage());
        verify(sessionGateway, never()).existsByStudentIdAndExamHistoryId(anyLong(), anyLong());
    }
}
