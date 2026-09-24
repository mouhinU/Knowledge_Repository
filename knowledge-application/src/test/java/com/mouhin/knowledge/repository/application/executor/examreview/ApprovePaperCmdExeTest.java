package com.mouhin.knowledge.repository.application.executor.examreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.application.executor.examgeneration.ExamQuestionSplitSupport;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.service.ExamContractValidator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 校对通过并发布执行器单测：锁定发布链路—— （1）契约校验通过 → 状态转 PUBLISHED、审核人 / 审核时间回写审计列； （2）生成失败（FAILED）→ 直接拒绝发布；（3）校验不通过
 * → 原样返回问题清单且不落库； （4）题目行为空 → 先惰性回灌（出卷即切分）再校验； （5）空白审核人回退 "admin"；已作废试卷未被门禁拦截（当前行为，附可疑标注）。
 *
 * @author mouhinU
 * @date 2026-09-24 18:15:00
 */
@DisplayName("校对发布执行器 (ApprovePaperCmdExe)")
class ApprovePaperCmdExeTest {

    private static final String SESSION_KEY = "sess-approve-1";

    private final PaperReviewSupport support = mock(PaperReviewSupport.class);
    private final ExamHistoryGateway examHistoryGateway = mock(ExamHistoryGateway.class);
    private final ExamQuestionSplitSupport splitSupport = mock(ExamQuestionSplitSupport.class);
    private final ApprovePaperCmdExe exe =
            new ApprovePaperCmdExe(support, examHistoryGateway, splitSupport);

    private ExamHistory stubHistory(String status) {
        ExamHistory history = new ExamHistory();
        history.setId(1L);
        history.setSessionId(SESSION_KEY);
        history.setExamPaper("# 试卷原文\n");
        history.setAnswerKey("# 答案\n");
        history.setStatus(status);
        when(support.requireHistory(SESSION_KEY)).thenReturn(history);
        return history;
    }

    private void stubQuestions(int count) {
        List<ExamQuestion> questions = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            questions.add(new ExamQuestion());
        }
        when(support.listQuestions(SESSION_KEY)).thenReturn(questions);
    }

    @Test
    @DisplayName("校验通过 → 状态转 PUBLISHED，审核人 / 时间回写审计列并落库")
    void approvedPaperPublishedWithAuditFields() {
        stubHistory(ExamHistory.STATUS_REVIEWABLE);
        stubQuestions(2);
        ExamContractValidator.Result pass = new ExamContractValidator.Result(true, List.of());
        when(support.validate(anyList(), isNull())).thenReturn(pass);

        ExamContractValidator.Result result = exe.execute(SESSION_KEY, "teacher-A");

        assertThat(result.pass()).isTrue();
        ArgumentCaptor<ExamHistory> cap = ArgumentCaptor.forClass(ExamHistory.class);
        verify(examHistoryGateway, times(1)).update(cap.capture());
        ExamHistory saved = cap.getValue();
        assertThat(saved.getStatus()).isEqualTo(ExamHistory.STATUS_PUBLISHED);
        assertThat(saved.getReviewedBy()).isEqualTo("teacher-A");
        assertThat(saved.getReviewedTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
    }

    @Test
    @DisplayName("生成失败（FAILED）→ 拒绝发布，不跑校验不落库")
    void failedPaperCannotPublish() {
        stubHistory(ExamHistory.STATUS_FAILED);

        assertThatThrownBy(() -> exe.execute(SESSION_KEY, "teacher-A"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生成失败");
        verify(support, never()).validate(anyList(), any());
        verify(examHistoryGateway, never()).update(any());
    }

    @Test
    @DisplayName("契约校验不通过 → 返回问题清单，状态保持待校对，不落库")
    void validationFailureBlocksPublish() {
        stubHistory(ExamHistory.STATUS_REVIEWABLE);
        stubQuestions(2);
        ExamContractValidator.Result fail =
                new ExamContractValidator.Result(false, List.of("第 1 题 缺少标准答案"));
        when(support.validate(anyList(), isNull())).thenReturn(fail);

        ExamContractValidator.Result result = exe.execute(SESSION_KEY, "teacher-A");

        assertThat(result.pass()).isFalse();
        assertThat(result.issues()).containsExactly("第 1 题 缺少标准答案");
        verify(examHistoryGateway, never()).update(any());
    }

    @Test
    @DisplayName("题目行为空 → 先惰性回灌切分（splitAndPersist）再取题校验发布")
    void emptyQuestionsTriggerLazySplit() {
        stubHistory(ExamHistory.STATUS_REVIEWABLE);
        ExamQuestion question = new ExamQuestion();
        when(support.listQuestions(SESSION_KEY))
                .thenReturn(List.of())
                .thenReturn(List.of(question));
        when(support.validate(eq(List.of(question)), isNull()))
                .thenReturn(new ExamContractValidator.Result(true, List.of()));

        exe.execute(SESSION_KEY, "teacher-A");

        verify(splitSupport, times(1))
                .splitAndPersist(eq(SESSION_KEY), eq("# 试卷原文\n"), eq("# 答案\n"), isNull());
        verify(support, times(2)).listQuestions(SESSION_KEY);
        verify(examHistoryGateway).update(any());
    }

    @Test
    @DisplayName(
            "空白审核人回退 admin；已作废（VOIDED）试卷当前仍可通过校验被发布（TODO(行为可疑): 发布门禁仅拦 FAILED，未拦 VOIDED，存在作废卷复活风险）")
    void blankReviewerDefaultsToAdminAndVoidedPaperNotBlocked() {
        stubHistory(ExamHistory.STATUS_VOIDED);
        stubQuestions(1);
        when(support.validate(anyList(), isNull()))
                .thenReturn(new ExamContractValidator.Result(true, List.of()));

        exe.execute(SESSION_KEY, "  ");

        ArgumentCaptor<ExamHistory> cap = ArgumentCaptor.forClass(ExamHistory.class);
        verify(examHistoryGateway).update(cap.capture());
        assertThat(cap.getValue().getReviewedBy()).isEqualTo("admin");
        assertThat(cap.getValue().getStatus()).isEqualTo(ExamHistory.STATUS_PUBLISHED);
    }
}
