package com.mouhin.knowledge.repository.application.executor.wronganswer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.application.service.ExamStructuredQuestionSupport;
import com.mouhin.knowledge.repository.client.dto.WrongAnswerVO;
import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 错题列表查询执行器单测：锁定无场次短路、错题口径（得分<满分 含部分得分）、题型过滤、 主题小写包含过滤、考生名解析与'未知考生'兜底、提交时间倒序与结构化题目行回填。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("错题列表查询执行器 (WrongAnswerListQryExe)")
class WrongAnswerListQryExeTest {

    private final ExamAnswerGateway examAnswerGateway = mock(ExamAnswerGateway.class);
    private final ExamSessionGateway examSessionGateway = mock(ExamSessionGateway.class);
    private final StudentGateway studentGateway = mock(StudentGateway.class);
    private final ExamStructuredQuestionSupport structuredQuestionSupport =
            mock(ExamStructuredQuestionSupport.class);
    private final WrongAnswerListQryExe exe =
            new WrongAnswerListQryExe(
                    examAnswerGateway,
                    examSessionGateway,
                    studentGateway,
                    structuredQuestionSupport);

    private ExamSession session(long id, Long studentId, String topic, LocalDateTime submit) {
        ExamSession s = new ExamSession();
        s.setId(id);
        s.setSessionKey("sk-" + id);
        s.setStudentId(studentId);
        s.setTopic(topic);
        s.setStatus("AI_GRADED");
        s.setSubmitTime(submit);
        return s;
    }

    private ExamAnswer answer(long id, Long sessionId, Integer number, String type, int effScore) {
        ExamAnswer a = new ExamAnswer();
        a.setId(id);
        a.setSessionId(sessionId);
        a.setQuestionNumber(number);
        a.setQuestionIndex(number);
        a.setQuestionType(type);
        a.setQuestionContent("题目" + number);
        a.setMaxScore(10);
        a.setAiScore(effScore);
        return a;
    }

    @Test
    @DisplayName("无已评分场次 → 返回空列表，不再查答题记录")
    void emptySessionsShortCircuits() {
        when(examSessionGateway.listByStudentIdAndStatuses(eq(1L), anyList()))
                .thenReturn(List.of());

        assertThat(exe.execute(1L, null, null)).isEmpty();
        verify(examAnswerGateway, never()).listBySessionIds(anyList());
    }

    @Test
    @DisplayName("错题口径：满分题剔除、部分得分保留；studentId 为 null 走全量场次查询")
    void filtersWrongAnswersAndNullStudentUsesGlobalQuery() {
        ExamSession s = session(100L, 7L, "函数专题", LocalDateTime.of(2026, 9, 1, 10, 0));
        when(examSessionGateway.listByStatuses(anyList())).thenReturn(List.of(s));
        when(examAnswerGateway.listBySessionIds(List.of(100L)))
                .thenReturn(
                        List.of(
                                answer(1L, 100L, 1, "SINGLE_CHOICE", 10),
                                answer(2L, 100L, 2, "SINGLE_CHOICE", 0),
                                answer(3L, 100L, 3, "SHORT_ANSWER", 4)));
        when(structuredQuestionSupport.loadByQuestionNumber(s)).thenReturn(Map.of());
        Student student = new Student();
        student.setId(7L);
        student.setDisplayName("小明");
        when(studentGateway.findById(7L)).thenReturn(Optional.of(student));

        List<WrongAnswerVO> result = exe.execute(null, null, null);

        verify(examSessionGateway).listByStatuses(anyList());
        assertThat(result).extracting(WrongAnswerVO::getAnswerId).containsExactly(2L, 3L);
        assertThat(result).allSatisfy(vo -> assertThat(vo.getStudentName()).isEqualTo("小明"));
    }

    @Test
    @DisplayName("题型过滤：questionType 精确匹配在错题集上二次收敛")
    void questionTypeFilterApplied() {
        ExamSession s = session(101L, 7L, "专题", LocalDateTime.of(2026, 9, 1, 10, 0));
        when(examSessionGateway.listByStudentIdAndStatuses(eq(7L), anyList()))
                .thenReturn(List.of(s));
        when(examAnswerGateway.listBySessionIds(List.of(101L)))
                .thenReturn(
                        List.of(
                                answer(1L, 101L, 1, "SINGLE_CHOICE", 0),
                                answer(2L, 101L, 2, "SHORT_ANSWER", 0)));
        when(structuredQuestionSupport.loadByQuestionNumber(s)).thenReturn(Map.of());

        List<WrongAnswerVO> result = exe.execute(7L, null, "SHORT_ANSWER");

        assertThat(result).singleElement().extracting(WrongAnswerVO::getAnswerId).isEqualTo(2L);
    }

    @Test
    @DisplayName("主题过滤大小写不敏感 + 考生查不到 → 显示名兜底'未知考生'")
    void topicFilterCaseInsensitiveAndUnknownStudent() {
        ExamSession math = session(102L, 8L, "Math 函数", LocalDateTime.of(2026, 9, 2, 8, 0));
        ExamSession chinese = session(103L, 9L, "语文古诗", LocalDateTime.of(2026, 9, 2, 9, 0));
        when(examSessionGateway.listByStatuses(anyList())).thenReturn(List.of(math, chinese));
        when(examAnswerGateway.listBySessionIds(List.of(102L)))
                .thenReturn(List.of(answer(5L, 102L, 1, "SINGLE_CHOICE", 0)));
        when(structuredQuestionSupport.loadByQuestionNumber(math)).thenReturn(Map.of());
        when(structuredQuestionSupport.loadByQuestionNumber(chinese)).thenReturn(Map.of());
        when(studentGateway.findById(8L)).thenReturn(Optional.empty());

        List<WrongAnswerVO> result = exe.execute(null, "MATH", null);

        assertThat(result)
                .singleElement()
                .extracting(WrongAnswerVO::getStudentName)
                .isEqualTo("未知考生");
        verify(examAnswerGateway, never()).listBySessionIds(List.of(103L));
    }

    @Test
    @DisplayName("提交时间倒序 + 结构化题目行回填（answer.correctAnswer 空时回退题目行）")
    void sortedBySubmitTimeDescAndQuestionEnriched() {
        ExamSession early = session(200L, 7L, "T", LocalDateTime.of(2026, 9, 1, 8, 0));
        ExamSession late = session(201L, 7L, "T", LocalDateTime.of(2026, 9, 3, 8, 0));
        when(examSessionGateway.listByStudentIdAndStatuses(eq(7L), anyList()))
                .thenReturn(List.of(early, late));
        when(examAnswerGateway.listBySessionIds(List.of(200L, 201L)))
                .thenReturn(
                        List.of(
                                answer(11L, 200L, 1, "SINGLE_CHOICE", 0),
                                answer(12L, 201L, 1, "SINGLE_CHOICE", 0)));
        ExamQuestion q = new ExamQuestion();
        q.setQuestionNumber(1);
        q.setCorrectAnswer("B");
        q.setAnalysis("因为……");
        when(structuredQuestionSupport.loadByQuestionNumber(early)).thenReturn(Map.of(1, q));
        when(structuredQuestionSupport.loadByQuestionNumber(late)).thenReturn(Map.of(1, q));
        Student student = new Student();
        student.setId(7L);
        student.setUsername("alice");
        when(studentGateway.findById(7L)).thenReturn(Optional.of(student));

        List<WrongAnswerVO> result = exe.execute(7L, null, null);

        assertThat(result).extracting(WrongAnswerVO::getSessionId).containsExactly(201L, 200L);
        assertThat(result.get(0).getAnalysis()).isEqualTo("因为……");
        assertThat(result.get(0).getCorrectAnswer()).isEqualTo("B");
        // displayName 为空时回退 username
        assertThat(result.get(0).getStudentName()).isEqualTo("alice");
        verify(examAnswerGateway).listBySessionIds(anyList());
    }
}
