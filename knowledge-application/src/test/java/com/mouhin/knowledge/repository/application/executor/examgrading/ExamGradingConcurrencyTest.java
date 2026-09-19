package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.application.executor.examgeneration.ExamQuestionSplitSupport;
import com.mouhin.knowledge.repository.application.service.ExamStructuredQuestionSupport;
import com.mouhin.knowledge.repository.domain.gateway.ExamAlertGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.service.ExamGradingProgressCallback;
import com.mouhin.knowledge.repository.domain.service.StreamingChatGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评分并发状态机回归测试（V2 阶段 2-B / 体检 P4 / CONC-1 围栏令牌）。
 * <p>
 * 锁定四项并发正确性保障：入口 {@code SUBMITTED→GRADING} <b>带围栏令牌</b>认领、逐题心跳以令牌续约、
 * 终态 {@code GRADING→AI_GRADED} 以令牌 CAS 落库、认领后异常以令牌安全回退。验证：认领失败（返回 null
 * 令牌）不重复评分；心跳/终态令牌失配（已被超时回收并重新认领）时立即停止且<b>绝不</b>上报完成或盲写覆盖；
 * 评分过程异常时以本次令牌回退、不误伤接管者——杜绝新旧评分者对同一场次交叉写。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@DisplayName("评分并发状态机 (围栏令牌认领 / 心跳 / 终态 CAS / 异常回退)")
class ExamGradingConcurrencyTest {

    private static final long SESSION_ID = 7L;
    /** claimForGrading 认领成功时返回的围栏令牌桩值。 */
    private static final String TOKEN = "fencing-token-abc";

    private final ExamSessionGateway sessionGateway = mock(ExamSessionGateway.class);
    private final ExamAnswerGateway answerGateway = mock(ExamAnswerGateway.class);
    private final ExamQuestionGateway questionGateway = mock(ExamQuestionGateway.class);
    private final ExamStructuredQuestionSupport structuredSupport = mock(ExamStructuredQuestionSupport.class);
    private final ExamQuestionSplitSupport splitSupport = mock(ExamQuestionSplitSupport.class);
    private final StreamingChatGateway streamingGateway = mock(StreamingChatGateway.class);
    private final ExamAlertGateway alertGateway = mock(ExamAlertGateway.class);

    private final ExamGradingSupport support = new ExamGradingSupport(
            sessionGateway, answerGateway, questionGateway,
            structuredSupport, splitSupport, streamingGateway, alertGateway);

    private ExamSession session() {
        ExamSession s = new ExamSession();
        s.setId(SESSION_ID);
        s.setStudentId(1L);
        s.setStatus("GRADING");
        return s;
    }

    private ExamAnswer objective(int index, String correct, String student, int max) {
        ExamAnswer a = new ExamAnswer();
        a.setSessionId(SESSION_ID);
        a.setQuestionIndex(index);
        a.setQuestionNumber(index);
        a.setQuestionType("SINGLE_CHOICE");
        a.setQuestionContent("题" + index);
        a.setCorrectAnswer(correct);
        a.setStudentAnswer(student);
        a.setMaxScore(max);
        return a;
    }

    /** 认领成功、心跳持续、终态 CAS 成功 → 逐题更新 + 恰好一次 completeGrading + onComplete。 */
    @Test
    @DisplayName("正常路径：令牌认领→令牌心跳→令牌终态 CAS→onComplete")
    void happyPathClaimsHeartbeatsAndCompletesViaCas() {
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(sessionGateway.claimForGrading(SESSION_ID)).thenReturn(TOKEN);
        when(structuredSupport.resolvePaperSessionKey(any())).thenReturn(null); // 空标准答案映射，跳过题目查询
        when(answerGateway.listBySessionId(SESSION_ID))
                .thenReturn(List.of(objective(1, "B", "B", 3), objective(2, "A", "C", 2)));
        when(sessionGateway.touchGradingHeartbeat(SESSION_ID, TOKEN)).thenReturn(true);
        when(sessionGateway.completeGrading(eq(SESSION_ID), eq(TOKEN), eq("AI_GRADED"), anyInt(), anyInt()))
                .thenReturn(true);

        ExamGradingProgressCallback callback = mock(ExamGradingProgressCallback.class);
        support.gradeExamInternal(SESSION_ID, callback);

        verify(sessionGateway).claimForGrading(SESSION_ID);
        // 2 题各一次 update + 各一次心跳（心跳须携带认领令牌）
        verify(answerGateway, times(2)).update(any(ExamAnswer.class));
        verify(sessionGateway, times(2)).touchGradingHeartbeat(SESSION_ID, TOKEN);
        // 总分 = 结构化满分缺失时回退答案行合计 = 3 + 2 = 5；AI 得分 = 满分题(3) + 0
        // 终态 CAS 必须携带与认领相同的令牌
        verify(sessionGateway).completeGrading(eq(SESSION_ID), eq(TOKEN), eq("AI_GRADED"), eq(3), eq(5));
        verify(callback).onComplete(eq(2), eq(3));
        verify(callback, never()).onError(any());
        // 已改走 CAS 终态，盲写 update(session) 不再被调用
        verify(sessionGateway, never()).update(any(ExamSession.class));
    }

    /** 认领失败（返回 null 令牌，已有评分者持有本场次）→ 不评分、不落任何写，优雅 onComplete(0,0)。 */
    @Test
    @DisplayName("认领失败：跳过重复评分，不触碰答题与终态")
    void claimLostShortCircuits() {
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(sessionGateway.claimForGrading(SESSION_ID)).thenReturn(null);

        ExamGradingProgressCallback callback = mock(ExamGradingProgressCallback.class);
        support.gradeExamInternal(SESSION_ID, callback);

        verify(answerGateway, never()).update(any(ExamAnswer.class));
        verify(sessionGateway, never()).touchGradingHeartbeat(anyLong(), anyString());
        verify(sessionGateway, never()).completeGrading(anyLong(), anyString(), anyString(), anyInt(), anyInt());
        // 未认领成功，绝不应回退他人锁
        verify(sessionGateway, never()).releaseGradingToSubmitted(anyLong(), anyString());
        verify(callback).onComplete(eq(0), eq(0));
        verify(callback, never()).onError(any());
    }

    /** 评分中途心跳令牌失配（场次被超时回收并重新认领）→ 停止，绝不 completeGrading、绝不 onComplete。 */
    @Test
    @DisplayName("心跳丢失：立即停止且放弃终态写入，避免与接管者交叉写")
    void heartbeatLostStopsBeforeTerminalWrite() {
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(sessionGateway.claimForGrading(SESSION_ID)).thenReturn(TOKEN);
        when(structuredSupport.resolvePaperSessionKey(any())).thenReturn(null);
        when(answerGateway.listBySessionId(SESSION_ID))
                .thenReturn(List.of(objective(1, "B", "B", 3), objective(2, "A", "C", 2)));
        when(sessionGateway.touchGradingHeartbeat(SESSION_ID, TOKEN)).thenReturn(true).thenReturn(false);

        ExamGradingProgressCallback callback = mock(ExamGradingProgressCallback.class);
        support.gradeExamInternal(SESSION_ID, callback);

        // 第 1 题正常入库，第 2 题评分后心跳令牌失配 → break；因非异常路径，不做回退（所有权已交出）
        verify(answerGateway, times(2)).update(any(ExamAnswer.class));
        verify(sessionGateway, times(2)).touchGradingHeartbeat(SESSION_ID, TOKEN);
        verify(sessionGateway, never()).completeGrading(anyLong(), anyString(), anyString(), anyInt(), anyInt());
        verify(sessionGateway, never()).releaseGradingToSubmitted(anyLong(), anyString());
        verify(callback, never()).onComplete(anyInt(), anyInt());
        verify(callback).onError(any());
    }

    /** 终态 CAS 令牌失配（提交瞬间已被回收重新认领）→ 不 onComplete，改 onError，结果不被采纳。 */
    @Test
    @DisplayName("终态 CAS 失败：慢速评分者不得盲写覆盖")
    void terminalCasLostDiscardsResult() {
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(sessionGateway.claimForGrading(SESSION_ID)).thenReturn(TOKEN);
        when(structuredSupport.resolvePaperSessionKey(any())).thenReturn(null);
        when(answerGateway.listBySessionId(SESSION_ID)).thenReturn(List.of(objective(1, "B", "B", 3)));
        when(sessionGateway.touchGradingHeartbeat(SESSION_ID, TOKEN)).thenReturn(true);
        when(sessionGateway.completeGrading(eq(SESSION_ID), eq(TOKEN), eq("AI_GRADED"), anyInt(), anyInt()))
                .thenReturn(false);

        ExamGradingProgressCallback callback = mock(ExamGradingProgressCallback.class);
        support.gradeExamInternal(SESSION_ID, callback);

        verify(sessionGateway).completeGrading(eq(SESSION_ID), eq(TOKEN), eq("AI_GRADED"), anyInt(), anyInt());
        verify(sessionGateway, never()).update(any(ExamSession.class));
        verify(callback, never()).onComplete(anyInt(), anyInt());
        verify(callback).onError(any());
    }

    /** DATA-5：全空答卷（零作答行）也必须落到终态，而非停在 SUBMITTED。 */
    @Test
    @DisplayName("全空答卷：令牌认领→零分终态 CAS→onComplete(0,0)，不落中间态")
    void emptyAnswersStillReachTerminalState() {
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(sessionGateway.claimForGrading(SESSION_ID)).thenReturn(TOKEN);
        when(structuredSupport.resolvePaperSessionKey(any())).thenReturn(null);
        when(answerGateway.listBySessionId(SESSION_ID)).thenReturn(List.of());
        when(sessionGateway.completeGrading(eq(SESSION_ID), eq(TOKEN), eq("AI_GRADED"), anyInt(), anyInt()))
                .thenReturn(true);

        ExamGradingProgressCallback callback = mock(ExamGradingProgressCallback.class);
        support.gradeExamInternal(SESSION_ID, callback);

        // 空卷仍须认领并最终落终态，得分/总分均为 0
        verify(sessionGateway).claimForGrading(SESSION_ID);
        verify(sessionGateway).completeGrading(eq(SESSION_ID), eq(TOKEN), eq("AI_GRADED"), eq(0), eq(0));
        verify(callback).onComplete(eq(0), eq(0));
        verify(callback, never()).onError(any());
        // 逐题更新与心跳在零题循环下不应发生
        verify(answerGateway, never()).update(any(ExamAnswer.class));
        verify(sessionGateway, never()).touchGradingHeartbeat(anyLong(), anyString());
    }

    /**
     * CONC-1：认领后评分过程抛异常 → 以本次围栏令牌安全回退为 SUBMITTED 并向上抛出，
     * 使场次能立即重新纳入调度，且绝不落终态。
     */
    @Test
    @DisplayName("认领后异常：以令牌安全回退并上抛，不落终态")
    void exceptionAfterClaimReleasesWithToken() {
        when(sessionGateway.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(sessionGateway.claimForGrading(SESSION_ID)).thenReturn(TOKEN);
        when(structuredSupport.resolvePaperSessionKey(any())).thenReturn(null);
        when(answerGateway.listBySessionId(SESSION_ID)).thenReturn(List.of(objective(1, "B", "B", 3)));
        // 心跳阶段抛出运行时异常，模拟评分中途失败
        when(sessionGateway.touchGradingHeartbeat(SESSION_ID, TOKEN))
                .thenThrow(new RuntimeException("boom"));

        ExamGradingProgressCallback callback = mock(ExamGradingProgressCallback.class);
        assertThrows(RuntimeException.class, () -> support.gradeExamInternal(SESSION_ID, callback));

        // 异常后须以认领令牌回退（token-guarded release），并绝不调用终态 CAS
        verify(sessionGateway).releaseGradingToSubmitted(SESSION_ID, TOKEN);
        verify(sessionGateway, never()).completeGrading(anyLong(), anyString(), anyString(), anyInt(), anyInt());
        verify(callback, never()).onComplete(anyInt(), anyInt());
    }
}
