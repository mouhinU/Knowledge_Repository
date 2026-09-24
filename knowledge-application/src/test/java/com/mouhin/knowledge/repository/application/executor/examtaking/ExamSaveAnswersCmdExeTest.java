package com.mouhin.knowledge.repository.application.executor.examtaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 保存 / 更新答题执行器单测（ExamSaveAnswersCmdExe）。
 *
 * <p>锁定断点续答链路的编排：结构化题目行为权威源逐题落行（未答也占行，分母不虚高）、 非 IN_PROGRESS 状态拒绝改动、越权访问拒绝、结构化行缺失时回退投影解析。
 *
 * @author mouhinU
 * @date 2026-09-24 17:27:38
 */
@DisplayName("保存答题执行器 (ExamSaveAnswersCmdExe)")
class ExamSaveAnswersCmdExeTest {

    private static final String SESSION_KEY = "sess-save-1";
    private static final String TOKEN = "stu-token";

    private final ExamAnswerGateway answerGateway = mock(ExamAnswerGateway.class);
    private final ExamSessionGateway sessionGateway = mock(ExamSessionGateway.class);
    private final ExamTakingSupport support = mock(ExamTakingSupport.class);
    private final ExamSaveAnswersCmdExe exe =
            new ExamSaveAnswersCmdExe(answerGateway, sessionGateway, support);

    private ExamSession inProgressSession() {
        ExamSession session = new ExamSession();
        session.setId(50L);
        session.setSessionKey(SESSION_KEY);
        session.setStudentId(7L);
        session.setStatus("IN_PROGRESS");
        session.setQuestionsJson("[]");
        when(support.resolveSession(SESSION_KEY, TOKEN)).thenReturn(session);
        return session;
    }

    private ExamQuestion question(int number, String type, int maxScore) {
        ExamQuestion q = new ExamQuestion();
        q.setQuestionNumber(number);
        q.setQuestionType(type);
        q.setStem("第" + number + "题题干");
        q.setMaxScore(maxScore);
        q.setOptionsJson(null);
        return q;
    }

    @Test
    @DisplayName("结构化题目行为权威源 → 每题必落一行（未答置空），先清后存并回写场次")
    void savesEveryStructuredQuestion() {
        inProgressSession();
        when(support.listPaperQuestions(any()))
                .thenReturn(
                        List.of(question(1, "SINGLE_CHOICE", 5), question(2, "SHORT_ANSWER", 10)));
        // 学生只答了第 1 题
        List<Map<String, String>> answers = List.of(Map.of("questionNumber", "1", "answer", "A"));

        exe.execute(SESSION_KEY, TOKEN, answers);

        verify(answerGateway).deleteBySessionId(50L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExamAnswer>> cap = ArgumentCaptor.forClass(List.class);
        verify(answerGateway).saveAll(cap.capture());
        List<ExamAnswer> saved = cap.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getStudentAnswer()).isEqualTo("A");
        assertThat(saved.get(0).getMaxScore()).isEqualTo(5);
        assertThat(saved.get(0).getSessionId()).isEqualTo(50L);
        assertThat(saved.get(1).getStudentAnswer()).isNull();
        assertThat(saved.get(1).getMaxScore()).isEqualTo(10);
        verify(sessionGateway).update(any());
    }

    @Test
    @DisplayName("场次非 IN_PROGRESS（已提交 / 结束）→ 抛'考试已结束'，不清不存")
    void rejectsFinishedSession() {
        ExamSession session = new ExamSession();
        session.setId(51L);
        session.setStatus("SUBMITTED");
        when(support.resolveSession(SESSION_KEY, TOKEN)).thenReturn(session);

        assertThatThrownBy(() -> exe.execute(SESSION_KEY, TOKEN, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已结束");
        verify(answerGateway, never()).deleteBySessionId(anyLong());
        verify(answerGateway, never()).saveAll(anyList());
        verify(sessionGateway, never()).update(any());
    }

    @Test
    @DisplayName("越权访问他人场次 → resolveSession 抛'无权访问'，不落答案")
    void rejectsForeignSession() {
        when(support.resolveSession(SESSION_KEY, TOKEN))
                .thenThrow(new IllegalArgumentException("无权访问此考试"));

        assertThatThrownBy(() -> exe.execute(SESSION_KEY, TOKEN, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无权");
        verify(answerGateway, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("结构化题目行缺失 → 回退投影 questionsJson 逐条落行")
    void fallsBackToProjectionWhenStructuredRowsAbsent() {
        ExamSession session = inProgressSession();
        when(support.listPaperQuestions(any())).thenReturn(List.of());
        when(support.parseQuestions(session.getQuestionsJson()))
                .thenReturn(List.of(Map.of("index", 1, "type", "SINGLE_CHOICE", "maxScore", 4)));
        when(support.findQuestion(anyList(), anyInt()))
                .thenReturn(Map.of("index", 1, "type", "SINGLE_CHOICE", "maxScore", 4));
        List<Map<String, String>> answers = List.of(Map.of("questionIndex", "1", "answer", "B"));

        exe.execute(SESSION_KEY, TOKEN, answers);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExamAnswer>> cap = ArgumentCaptor.forClass(List.class);
        verify(answerGateway).saveAll(cap.capture());
        assertThat(cap.getValue()).hasSize(1);
        assertThat(cap.getValue().get(0).getStudentAnswer()).isEqualTo("B");
        assertThat(cap.getValue().get(0).getQuestionType()).isEqualTo("SINGLE_CHOICE");
    }

    @Test
    @DisplayName("投影回退时题号非数字 → Integer.parseInt 抛 NumberFormatException，不落库")
    void projectionRejectsNonNumericIndex() {
        ExamSession session = inProgressSession();
        when(support.listPaperQuestions(any())).thenReturn(List.of());
        when(support.parseQuestions(session.getQuestionsJson())).thenReturn(List.of());
        List<Map<String, String>> answers = List.of(Map.of("questionIndex", "abc", "answer", "X"));

        assertThatThrownBy(() -> exe.execute(SESSION_KEY, TOKEN, answers))
                .isInstanceOf(NumberFormatException.class);
        verify(answerGateway, never()).saveAll(anyList());
    }
}
