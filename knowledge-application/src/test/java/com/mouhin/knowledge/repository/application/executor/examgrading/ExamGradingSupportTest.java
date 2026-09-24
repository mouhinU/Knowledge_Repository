package com.mouhin.knowledge.repository.application.executor.examgrading;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.application.executor.examgeneration.ExamQuestionSplitSupport;
import com.mouhin.knowledge.repository.application.service.ExamStructuredQuestionSupport;
import com.mouhin.knowledge.repository.domain.gateway.ExamAlertGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.service.ExamGradingProgressCallback;
import com.mouhin.knowledge.repository.domain.service.StreamingChatGateway;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 考试评分支撑单测：锁定 {@code gradeExamInternal} 编排主干 —— 场次缺失 / 认领失败 / 心跳接管 / 认领后异常回退 等并发围栏分支，结构化标准答案回填与
 * 「缺答案判 0 告警」门禁，主观题 AI 分数解析，以及空卷收敛终态。 客观题逐题判定矩阵已由 {@code ExamGradingCharacterizationTest}
 * 回归基线锁定，此处不重复。
 *
 * @author mouhinU
 * @date 2026-09-24 18:16:00
 */
@DisplayName("考试评分支撑编排 (ExamGradingSupport)")
class ExamGradingSupportTest {

    private static final Long SESSION_ID = 1L;
    private static final String TOKEN = "grading-token";
    private static final String PAPER_KEY = "paper-session-key";

    private final ExamSessionGateway sessionGateway = mock(ExamSessionGateway.class);
    private final ExamAnswerGateway answerGateway = mock(ExamAnswerGateway.class);
    private final ExamQuestionGateway questionGateway = mock(ExamQuestionGateway.class);
    private final ExamStructuredQuestionSupport structuredQuestionSupport =
            mock(ExamStructuredQuestionSupport.class);
    private final ExamQuestionSplitSupport splitSupport = mock(ExamQuestionSplitSupport.class);
    private final StreamingChatGateway streamingChatGateway = mock(StreamingChatGateway.class);
    private final ExamAlertGateway alertGateway = mock(ExamAlertGateway.class);

    private ExamGradingSupport support;

    @BeforeEach
    void setUp() {
        support =
                new ExamGradingSupport(
                        sessionGateway,
                        answerGateway,
                        questionGateway,
                        structuredQuestionSupport,
                        splitSupport,
                        streamingChatGateway,
                        alertGateway);
        when(structuredQuestionSupport.resolvePaperSessionKey(any())).thenReturn(PAPER_KEY);
    }

    private ExamSession submittedSession() {
        ExamSession session = new ExamSession();
        session.setId(SESSION_ID);
        session.setSessionKey("sess-1");
        session.setStatus("SUBMITTED");
        session.setTopic("期中数学");
        session.setDifficulty("MEDIUM");
        return session;
    }

    private ExamQuestion structured(int number, String type, String answer, int maxScore) {
        ExamQuestion q = new ExamQuestion();
        q.setQuestionNumber(number);
        q.setQuestionType(type);
        q.setCorrectAnswer(answer);
        q.setMaxScore(maxScore);
        return q;
    }

    private ExamAnswer answer(
            int index, Integer number, String type, String studentAnswer, int maxScore) {
        ExamAnswer a = new ExamAnswer();
        a.setId((long) index);
        a.setSessionId(SESSION_ID);
        a.setQuestionIndex(index);
        a.setQuestionNumber(number);
        a.setQuestionType(type);
        a.setStudentAnswer(studentAnswer);
        a.setMaxScore(maxScore);
        return a;
    }

    private void stubClaimPipeline() {
        when(sessionGateway.claimForGrading(SESSION_ID)).thenReturn(TOKEN);
        when(sessionGateway.touchGradingHeartbeat(SESSION_ID, TOKEN)).thenReturn(true);
        when(sessionGateway.completeGrading(
                        eq(SESSION_ID), eq(TOKEN), eq("AI_GRADED"), anyInt(), anyInt()))
                .thenReturn(true);
    }

    @Test
    @DisplayName("场次不存在 → IllegalArgumentException，不做认领")
    void missingSessionThrows() {
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> support.gradeExamInternal(SESSION_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("考试场次不存在");
        verify(sessionGateway, never()).claimForGrading(anyLong());
    }

    @Test
    @DisplayName("认领失败（已被其它流程持有）→ onComplete(0,0) 优雅结束，零写入")
    void failedClaimEndsGracefully() {
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(submittedSession()));
        when(answerGateway.listBySessionId(SESSION_ID)).thenReturn(List.of());
        when(sessionGateway.claimForGrading(SESSION_ID)).thenReturn(null);
        ExamGradingProgressCallback callback = mock(ExamGradingProgressCallback.class);

        support.gradeExamInternal(SESSION_ID, callback);

        verify(callback).onComplete(0, 0);
        verify(answerGateway, never()).update(any());
        verify(sessionGateway, never())
                .completeGrading(anyLong(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("客观题主干：结构化标准答案回填 → 判满分 → 终态 CAS 落 AI_GRADED（卷面总分取题目满分合计）")
    void objectiveHappyPathPersistsFinalState() {
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(submittedSession()));
        ExamAnswer a = answer(0, 1, "SINGLE_CHOICE", "B", 5);
        when(answerGateway.listBySessionId(SESSION_ID)).thenReturn(List.of(a));
        when(questionGateway.listBySessionKey(PAPER_KEY))
                .thenReturn(List.of(structured(1, "SINGLE_CHOICE", "B", 5)));
        stubClaimPipeline();
        ExamGradingProgressCallback callback = mock(ExamGradingProgressCallback.class);

        support.gradeExamInternal(SESSION_ID, callback);

        assertEquals(5, a.getAiScore(), "correctAnswer 应由结构化行回填并命中判满分");
        verify(sessionGateway).completeGrading(SESSION_ID, TOKEN, "AI_GRADED", 5, 5);
        verify(callback).onComplete(1, 5);
        verify(callback).onQuestionDone(eq(0), anyString(), eq(5), eq(5), anyString(), anyLong());
    }

    @Test
    @DisplayName("标准答案缺失：回填失败 → 判 0 分 + answerKeyMissing 告警，绝不静默给分")
    void missingCorrectAnswerForcesZeroAndAlerts() {
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(submittedSession()));
        ExamAnswer a = answer(0, 9, "SINGLE_CHOICE", "A", 5);
        when(answerGateway.listBySessionId(SESSION_ID)).thenReturn(List.of(a));
        // 结构化行缺失且惰性回灌后仍为空 → 回填失败
        when(questionGateway.listBySessionKey(PAPER_KEY)).thenReturn(List.of());
        stubClaimPipeline();

        support.gradeExamInternal(SESSION_ID, null);

        assertEquals(0, a.getAiScore());
        assertTrue(a.getAiFeedback().contains("缺少标准答案"));
        verify(alertGateway).answerKeyMissing(SESSION_ID, 9);
        verify(splitSupport).splitAndPersist(eq(PAPER_KEY), any(), any(), any());
        // 结构化行缺失 → 卷面总分回退答案行满分合计 5，得分仍 0
        verify(sessionGateway).completeGrading(SESSION_ID, TOKEN, "AI_GRADED", 0, 5);
    }

    @Test
    @DisplayName("心跳失配（场次被接管）→ 中断本流程并 onError，禁止写终态")
    void heartbeatLossAbortsWithoutFinalWrite() {
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(submittedSession()));
        when(answerGateway.listBySessionId(SESSION_ID))
                .thenReturn(List.of(answer(0, 1, "SINGLE_CHOICE", "B", 5)));
        when(questionGateway.listBySessionKey(PAPER_KEY))
                .thenReturn(List.of(structured(1, "SINGLE_CHOICE", "B", 5)));
        when(sessionGateway.claimForGrading(SESSION_ID)).thenReturn(TOKEN);
        when(sessionGateway.touchGradingHeartbeat(SESSION_ID, TOKEN)).thenReturn(false);
        ExamGradingProgressCallback callback = mock(ExamGradingProgressCallback.class);

        support.gradeExamInternal(SESSION_ID, callback);

        verify(callback).onError(anyString());
        ArgumentCaptor<String> errCap = ArgumentCaptor.forClass(String.class);
        verify(callback).onError(errCap.capture());
        assertTrue(errCap.getValue().contains("接管"));
        verify(sessionGateway, never())
                .completeGrading(anyLong(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("空答卷（0 行作答）也收敛终态：DATA-5 修复口径 → 0 分 + 卷面满分落 AI_GRADED")
    void emptyAnswersStillConverge() {
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(submittedSession()));
        when(answerGateway.listBySessionId(SESSION_ID)).thenReturn(List.of());
        when(questionGateway.listBySessionKey(PAPER_KEY))
                .thenReturn(
                        List.of(
                                structured(1, "SINGLE_CHOICE", "B", 5),
                                structured(2, "TRUE_FALSE", "正确", 3)));
        stubClaimPipeline();
        ExamGradingProgressCallback callback = mock(ExamGradingProgressCallback.class);

        support.gradeExamInternal(SESSION_ID, callback);

        verify(sessionGateway).completeGrading(SESSION_ID, TOKEN, "AI_GRADED", 0, 8);
        verify(callback).onComplete(0, 0);
    }

    @Test
    @DisplayName("主观题：AI 输出「分数：7」解析计分，maxScore 上限内生效")
    void subjectiveAiScoreParsed() {
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(submittedSession()));
        ExamAnswer a = answer(0, 1, "SHORT_ANSWER", "我的作答内容", 10);
        when(answerGateway.listBySessionId(SESSION_ID)).thenReturn(List.of(a));
        when(questionGateway.listBySessionKey(PAPER_KEY))
                .thenReturn(List.of(structured(1, "SHORT_ANSWER", "参考答案", 10)));
        stubClaimPipeline();
        when(streamingChatGateway.streamCompletion(anyString(), anyString(), any()))
                .thenReturn("分数：7\n理由：要点基本齐全");

        support.gradeExamInternal(SESSION_ID, null);

        assertEquals(7, a.getAiScore());
        assertTrue(a.getAiFeedback().contains("要点"));
        verify(sessionGateway).completeGrading(SESSION_ID, TOKEN, "AI_GRADED", 7, 10);
    }

    @Test
    @DisplayName("主观题未作答 → 跳过 AI 直接 0 分，不调模型")
    void blankSubjectiveSkipsAi() {
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(submittedSession()));
        ExamAnswer a = answer(0, 1, "ESSAY", "   ", 10);
        when(answerGateway.listBySessionId(SESSION_ID)).thenReturn(List.of(a));
        when(questionGateway.listBySessionKey(PAPER_KEY))
                .thenReturn(List.of(structured(1, "ESSAY", "参考答案", 10)));
        stubClaimPipeline();

        support.gradeExamInternal(SESSION_ID, null);

        assertEquals(0, a.getAiScore());
        assertTrue(a.getAiFeedback().contains("未作答"));
        verify(streamingChatGateway, never()).streamCompletion(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("认领后落库异常 → 以本次令牌回退 SUBMITTED 并上抛（不误伤接管者）")
    void failureAfterClaimReleasesOwnership() {
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(submittedSession()));
        when(answerGateway.listBySessionId(SESSION_ID))
                .thenReturn(List.of(answer(0, 1, "SINGLE_CHOICE", "B", 5)));
        when(questionGateway.listBySessionKey(PAPER_KEY))
                .thenReturn(List.of(structured(1, "SINGLE_CHOICE", "B", 5)));
        stubClaimPipeline();
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(answerGateway)
                .update(any());

        assertThatThrownBy(() -> support.gradeExamInternal(SESSION_ID, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");
        verify(sessionGateway).releaseGradingToSubmitted(SESSION_ID, TOKEN);
        verify(sessionGateway, never())
                .completeGrading(anyLong(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("triggerGradingAsync：非 SUBMITTED 状态 → onError 拒绝，不进入认领")
    void triggerRejectsNonSubmittedStatus() {
        ExamSession graded = submittedSession();
        graded.setStatus("AI_GRADED");
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(graded));
        ExamGradingProgressCallback callback = mock(ExamGradingProgressCallback.class);

        support.triggerGradingAsync(SESSION_ID, callback);

        verify(callback).onError(anyString());
        verify(sessionGateway, never()).claimForGrading(anyLong());
    }

    // 其他分支待补：gradeExamAsync 线程池拒绝分支与 ArticleGenerationSupport 同构（AbortPolicy），暂不重复建测。
}
