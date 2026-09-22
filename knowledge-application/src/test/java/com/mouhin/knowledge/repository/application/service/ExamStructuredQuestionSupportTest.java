package com.mouhin.knowledge.repository.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 结构化题目读取支撑的回归测试（改造：单一实现护航）。
 *
 * <p>本类冻结 {@link ExamStructuredQuestionSupport#resolvePaperSessionKey} 的四种解析口径， 以及 {@link
 * ExamStructuredQuestionSupport#loadByQuestionNumber} 的「按印刷题号建映射、跳过缺号」行为。 {@code
 * ExamGradingSupport}（评分）与成绩复核 / 错题本（展示）现均委托到本支撑的同一实现，
 * 因此这里即为跨层共用的权威口径基线：任何对这些分支的改动若偏离预期都会在此失败并强制确认。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
@DisplayName("结构化题目读取支撑 (纯读单一实现)")
class ExamStructuredQuestionSupportTest {

    private final ExamHistoryGateway examHistoryGateway = mock(ExamHistoryGateway.class);
    private final ExamQuestionGateway examQuestionGateway = mock(ExamQuestionGateway.class);
    private final ExamStructuredQuestionSupport support =
            new ExamStructuredQuestionSupport(examHistoryGateway, examQuestionGateway);

    private ExamSession session(Long historyId, String sessionKey) {
        ExamSession session = new ExamSession();
        session.setExamHistoryId(historyId);
        session.setSessionKey(sessionKey);
        return session;
    }

    private ExamHistory history(String sessionId) {
        ExamHistory history = new ExamHistory();
        history.setSessionId(sessionId);
        return history;
    }

    private ExamQuestion question(Integer number, String answer, String criteria) {
        ExamQuestion q = new ExamQuestion();
        q.setQuestionNumber(number);
        q.setCorrectAnswer(answer);
        q.setScoringCriteria(criteria);
        return q;
    }

    @Nested
    @DisplayName("试卷主键解析")
    class ResolvePaperKey {

        @Test
        @DisplayName("关联出卷历史且历史有 sessionId → 取历史 sessionId（生成期切分键）")
        void historyLinkedUsesHistorySessionId() {
            when(examHistoryGateway.findById(10L)).thenReturn(Optional.of(history("exam-hist-10")));

            assertThat(support.resolvePaperSessionKey(session(10L, "stu-session-7")))
                    .isEqualTo("exam-hist-10");
        }

        @Test
        @DisplayName("历史 sessionId 为空 → 回退场次自身 sessionKey")
        void blankHistorySessionIdFallsBackToSessionKey() {
            when(examHistoryGateway.findById(10L)).thenReturn(Optional.of(history("  ")));

            assertThat(support.resolvePaperSessionKey(session(10L, "stu-session-7")))
                    .isEqualTo("stu-session-7");
        }

        @Test
        @DisplayName("historyId 存在但历史缺失 → 回退场次自身 sessionKey")
        void missingHistoryFallsBackToSessionKey() {
            when(examHistoryGateway.findById(99L)).thenReturn(Optional.empty());

            assertThat(support.resolvePaperSessionKey(session(99L, "stu-session-7")))
                    .isEqualTo("stu-session-7");
        }

        @Test
        @DisplayName("即时卷（无 historyId）→ 直接用场次 sessionKey，不查历史")
        void instantPaperUsesOwnKeyWithoutHistoryLookup() {
            assertThat(support.resolvePaperSessionKey(session(null, "instant-key")))
                    .isEqualTo("instant-key");
            verify(examHistoryGateway, never()).findById(org.mockito.ArgumentMatchers.anyLong());
        }
    }

    @Nested
    @DisplayName("结构化行按题号建映射")
    class LoadByNumber {

        @Test
        @DisplayName("印刷题号 → 题目行；缺印刷题号的行被跳过")
        void mapsByQuestionNumberAndSkipsNulls() {
            when(examHistoryGateway.findById(10L)).thenReturn(Optional.of(history("exam-hist-10")));
            when(examQuestionGateway.listBySessionKey("exam-hist-10"))
                    .thenReturn(
                            List.of(
                                    question(1, "B", "选对得2分"),
                                    question(2, "正确", "每题1分"),
                                    question(null, "无号题应被忽略", null)));

            Map<Integer, ExamQuestion> map = support.loadByQuestionNumber(session(10L, "stu-7"));

            assertThat(map).containsOnlyKeys(1, 2);
            assertThat(map.get(2).getScoringCriteria()).isEqualTo("每题1分");
        }

        @Test
        @DisplayName("试卷主键无法解析（sessionKey 也为空）→ 返回空表且不查库")
        void blankPaperKeyReturnsEmpty() {
            Map<Integer, ExamQuestion> map = support.loadByQuestionNumber(session(null, null));

            assertThat(map).isEmpty();
            verify(examQuestionGateway, never()).listBySessionKey(anyString());
        }
    }
}
