package com.mouhin.knowledge.repository.application.executor.examtaking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.application.agent.ExamContentRenderAgent;
import com.mouhin.knowledge.repository.application.agent.ExamContentValidatorAgent;
import com.mouhin.knowledge.repository.application.agent.PaperValidationReport;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 在线做题共享支撑单测：锁定考生令牌鉴权（缺失 / 过期均回「登录已过期」，不区分原因防探测）、 场次归属校验、试卷题号键解析（历史卷 vs 即时卷）、卷面总分累加兜底
 * 100、渲染门禁与重解析探测。 配图注入分支已由 {@code ExamTakingSupportImagesTest} 覆盖，此处不重复。
 *
 * @author mouhinU
 * @date 2026-09-24 18:16:00
 */
@DisplayName("在线做题共享支撑 (ExamTakingSupport)")
class ExamTakingSupportTest {

    private static final String TOKEN = "stu-token";

    private final StudentGateway studentGateway = mock(StudentGateway.class);
    private final ExamSessionGateway sessionGateway = mock(ExamSessionGateway.class);
    private final ExamHistoryGateway historyGateway = mock(ExamHistoryGateway.class);
    private final ExamQuestionGateway questionGateway = mock(ExamQuestionGateway.class);
    private final ExamContentRenderAgent renderAgent = mock(ExamContentRenderAgent.class);
    private final ExamContentValidatorAgent validatorAgent = mock(ExamContentValidatorAgent.class);

    private final ExamTakingSupport support =
            new ExamTakingSupport(
                    studentGateway,
                    sessionGateway,
                    historyGateway,
                    questionGateway,
                    renderAgent,
                    validatorAgent);

    private Student validStudent(Long id) {
        Student student = new Student();
        student.setId(id);
        student.setSessionToken(TOKEN);
        student.setTokenExpiry(LocalDateTime.now().plusHours(1));
        return student;
    }

    @Test
    @DisplayName("令牌不存在于任何考生 → 抛「登录已过期，请重新登录」")
    void unknownTokenRejected() {
        when(studentGateway.findBySessionToken(TOKEN)).thenReturn(Optional.empty());

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> support.resolveStudent(TOKEN));
        assertTrue(ex.getMessage().contains("登录已过期"));
    }

    @Test
    @DisplayName("令牌命中但已过期（tokenExpiry 在过去）→ 同样回「登录已过期」")
    void expiredTokenRejected() {
        Student student = validStudent(9L);
        student.setTokenExpiry(LocalDateTime.now().minusMinutes(1));
        when(studentGateway.findBySessionToken(TOKEN)).thenReturn(Optional.of(student));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> support.resolveStudent(TOKEN));
        assertTrue(ex.getMessage().contains("登录已过期"));
    }

    @Test
    @DisplayName("有效令牌 → 返回考生主体")
    void validTokenResolvesStudent() {
        when(studentGateway.findBySessionToken(TOKEN)).thenReturn(Optional.of(validStudent(9L)));

        assertEquals(9L, support.resolveStudent(TOKEN).getId());
    }

    @Test
    @DisplayName("场次归属他人 → 抛「无权访问此考试」；场次不存在 → 抛含场次键的异常")
    void sessionOwnershipGuard() {
        when(studentGateway.findBySessionToken(TOKEN)).thenReturn(Optional.of(validStudent(9L)));
        ExamSession other = new ExamSession();
        other.setId(1L);
        other.setSessionKey("sess-1");
        other.setStudentId(123L);
        when(sessionGateway.findBySessionKey("sess-1")).thenReturn(Optional.of(other));

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> support.resolveSession("sess-1", TOKEN));
        assertTrue(ex.getMessage().contains("无权"));

        when(sessionGateway.findBySessionKey("sess-404")).thenReturn(Optional.empty());
        IllegalArgumentException missing =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> support.resolveSession("sess-404", TOKEN));
        assertTrue(missing.getMessage().contains("sess-404"));
    }

    @Test
    @DisplayName("试卷题号键：关联历史卷取历史 sessionId；历史缺失 / 空白回退场次自身键；即时卷直取场次键")
    void paperSessionKeyResolution() {
        ExamSession withHistory = new ExamSession();
        withHistory.setSessionKey("sess-1");
        withHistory.setExamHistoryId(66L);
        ExamHistory history = new ExamHistory();
        history.setSessionId("hist-session");
        when(historyGateway.findById(66L)).thenReturn(Optional.of(history));
        assertEquals("hist-session", support.resolvePaperSessionKey(withHistory));

        when(historyGateway.findById(66L)).thenReturn(Optional.empty());
        assertEquals("sess-1", support.resolvePaperSessionKey(withHistory), "历史缺失回退场次键");

        ExamHistory blankHistory = new ExamHistory();
        blankHistory.setSessionId("  ");
        when(historyGateway.findById(66L)).thenReturn(Optional.of(blankHistory));
        assertEquals("sess-1", support.resolvePaperSessionKey(withHistory), "历史键空白回退场次键");

        ExamSession instant = new ExamSession();
        instant.setSessionKey("sess-2");
        assertEquals("sess-2", support.resolvePaperSessionKey(instant));
    }

    @Test
    @DisplayName("卷面总分：maxScore 累加；解析失败 / 和为 0 → 兜底 100")
    void sumMaxScoreFallback() {
        assertEquals(15, support.sumMaxScore("[{\"maxScore\":5},{\"maxScore\":10}]"));
        assertEquals(100, support.sumMaxScore("not-a-json"), "解析失败兜底 100");
        assertEquals(100, support.sumMaxScore("[]"), "和为 0 兜底 100");
        assertEquals(
                100,
                support.sumMaxScore("[{\"maxScore\":\"abc\"}]"),
                "非数字 maxScore 不计入，总和 0 → 兜底 100");
    }

    @Test
    @DisplayName("渲染门禁：校验报告 pass=false → 抛「试卷内容校验未通过」并携带错误明细")
    void validateOrThrowBlocksOnErrors() {
        when(validatorAgent.validate(any()))
                .thenReturn(
                        new PaperValidationReport(false, 3, 90, List.of("第 2 题选项缺失"), List.of()));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> support.validateOrThrow("[]"));
        assertTrue(ex.getMessage().contains("第 2 题选项缺失"));
    }

    @Test
    @DisplayName("题目 JSON 解析与按 index 定位：空白入参回空列表；未命中回 null")
    void parseAndFindQuestion() {
        assertTrue(support.parseQuestions(null).isEmpty());
        assertTrue(support.parseQuestions("   ").isEmpty());
        assertTrue(support.parseQuestions("{{{bad").isEmpty(), "非法 JSON 静默回空列表");

        List<Map<String, Object>> questions =
                support.parseQuestions("[{\"index\":0},{\"index\":1}]");
        assertEquals(2, questions.size());
        assertEquals(1, support.findQuestion(questions, 1).get("index"));
        assertNull(support.findQuestion(questions, 9));
    }

    @Test
    @DisplayName("重解析探测：选择题选项少于 2 个 → true；正常 / 非选择题 / 非法 JSON → false")
    void needsReparseDetection() {
        assertTrue(
                support.needsReparse("[{\"type\":\"SINGLE_CHOICE\",\"options\":[\"A\"]}]"),
                "选项少于 2 个应触发重解析");
        assertFalse(
                support.needsReparse("[{\"type\":\"SINGLE_CHOICE\",\"options\":[\"A\",\"B\"]}]"));
        assertFalse(support.needsReparse("[{\"type\":\"ESSAY\",\"options\":[]}]"));
        assertFalse(support.needsReparse("bad json"), "解析失败保守返回 false 不触发");
    }

    // 其他分支待补：renderWithPlan / renderNoPlan 纯委托与 updateSession 落库透传（无独立逻辑）。
}
