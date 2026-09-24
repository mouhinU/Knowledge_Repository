package com.mouhin.knowledge.repository.application.executor.examreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.application.executor.examgeneration.ExamQuestionSplitSupport;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.service.ExamContractValidator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 重新切分试卷执行器单测：锁定「按原文回灌切分」的前置门禁与委派—— （1）原文存在 → 以 examPaper / answerKey / plan 调 splitAndPersist
 * 并原样返回其结果； （2）原文为 null / 空白 → 抛业务异常且不触发切分（防止空快照覆盖既有题目行）； （3）重切分不改变试卷状态、不做发布门禁，已作废卷同样允许重切（发布仍需另行通过
 * ApprovePaperCmdExe）。
 *
 * @author mouhinU
 * @date 2026-09-24 18:15:00
 */
@DisplayName("重新切分试卷执行器 (ResplitPaperCmdExe)")
class ResplitPaperCmdExeTest {

    private static final String SESSION_KEY = "sess-resplit-1";

    private final PaperReviewSupport support = mock(PaperReviewSupport.class);
    private final ExamQuestionSplitSupport splitSupport = mock(ExamQuestionSplitSupport.class);
    private final ResplitPaperCmdExe exe = new ResplitPaperCmdExe(support, splitSupport);

    private ExamHistory stubHistory(String examPaper) {
        ExamHistory history = new ExamHistory();
        history.setId(1L);
        history.setSessionId(SESSION_KEY);
        history.setExamPaper(examPaper);
        history.setAnswerKey("# 答案\n");
        history.setStatus(ExamHistory.STATUS_REVIEWABLE);
        when(support.requireHistory(SESSION_KEY)).thenReturn(history);
        return history;
    }

    @Test
    @DisplayName("原文存在 → 以原文三元组调 splitAndPersist，并原样返回切分结果")
    void splitsWithOriginalMarkdownAndReturnsOutcome() {
        ExamHistory history = stubHistory("# 试卷原文\n");
        ExamQuestionSplitSupport.SplitOutcome outcome =
                new ExamQuestionSplitSupport.SplitOutcome(
                        6, new ExamContractValidator.Result(true, List.of()));
        when(splitSupport.splitAndPersist(eq(SESSION_KEY), eq("# 试卷原文\n"), eq("# 答案\n"), isNull()))
                .thenReturn(outcome);

        ExamQuestionSplitSupport.SplitOutcome result = exe.execute(SESSION_KEY);

        assertThat(result).isSameAs(outcome);
        assertThat(result.count()).isEqualTo(6);
        verify(splitSupport, times(1))
                .splitAndPersist(
                        eq(SESSION_KEY),
                        eq(history.getExamPaper()),
                        eq(history.getAnswerKey()),
                        isNull());
    }

    @Test
    @DisplayName("原文为 null → 拒绝重切，不触发切分（防止空快照覆盖既有题目行）")
    void nullExamPaperRejected() {
        stubHistory(null);

        assertThatThrownBy(() -> exe.execute(SESSION_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("试卷原文为空");
        verify(splitSupport, never()).splitAndPersist(any(), any(), any(), any());
    }

    @Test
    @DisplayName("原文为空白字符串 → 同样拒绝重切")
    void blankExamPaperRejected() {
        stubHistory("   ");

        assertThatThrownBy(() -> exe.execute(SESSION_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("试卷原文为空");
        verify(splitSupport, never()).splitAndPersist(any(), any(), any(), any());
    }

    @Test
    @DisplayName("已作废（VOIDED）试卷仍允许重切分：本执行器不做状态门禁，仅原文非空即放行")
    void voidedPaperStillResplits() {
        ExamHistory history = stubHistory("# 试卷原文\n");
        history.setStatus(ExamHistory.STATUS_VOIDED);
        when(splitSupport.splitAndPersist(any(), any(), any(), any()))
                .thenReturn(
                        new ExamQuestionSplitSupport.SplitOutcome(
                                3, new ExamContractValidator.Result(false, List.of("x"))));

        ExamQuestionSplitSupport.SplitOutcome result = exe.execute(SESSION_KEY);

        assertThat(result.count()).isEqualTo(3);
        verify(splitSupport, times(1))
                .splitAndPersist(eq(SESSION_KEY), eq("# 试卷原文\n"), eq("# 答案\n"), isNull());
    }
}
