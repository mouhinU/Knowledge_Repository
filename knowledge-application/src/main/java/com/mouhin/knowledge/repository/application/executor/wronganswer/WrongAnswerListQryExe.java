package com.mouhin.knowledge.repository.application.executor.wronganswer;

import com.mouhin.knowledge.repository.application.converter.WrongAnswerConverter;
import com.mouhin.knowledge.repository.application.service.ExamStructuredQuestionSupport;
import com.mouhin.knowledge.repository.client.dto.WrongAnswerVO;
import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 错题列表核心查询执行器（app 层用例）
 * <p>
 * 基于已评分场次筛选答错 / 部分得分题目，按题型过滤并预读结构化题目行（答案 / 解析 / 评分标准），
 * 产出按提交时间倒序的错题视图列表。供分页与 AI 总结两个用例复用。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class WrongAnswerListQryExe {

    private static final Logger logger = LoggerFactory.getLogger(WrongAnswerListQryExe.class);

    /** 已评分的状态列表（AI_GRADED / REVIEWED / PUBLISHED） */
    private static final List<String> GRADED_STATUSES = List.of("AI_GRADED", "REVIEWED", "PUBLISHED");

    /** 缺省考生名 */
    private static final String UNKNOWN_STUDENT = "未知考生";

    private final ExamAnswerGateway examAnswerGateway;
    private final ExamSessionGateway examSessionGateway;
    private final StudentGateway studentGateway;
    private final ExamStructuredQuestionSupport structuredQuestionSupport;

    public WrongAnswerListQryExe(ExamAnswerGateway examAnswerGateway,
                                 ExamSessionGateway examSessionGateway,
                                 StudentGateway studentGateway,
                                 ExamStructuredQuestionSupport structuredQuestionSupport) {
        this.examAnswerGateway = examAnswerGateway;
        this.examSessionGateway = examSessionGateway;
        this.studentGateway = studentGateway;
        this.structuredQuestionSupport = structuredQuestionSupport;
    }

    public List<WrongAnswerVO> execute(Long studentId, String topic, String questionType) {
        // 1. 查询已评分的场次
        List<ExamSession> sessions = findGradedSessions(studentId, topic);
        if (sessions.isEmpty()) {
            return List.of();
        }

        // 2. 查询这些场次的所有答题记录
        List<Long> sessionIds = sessions.stream().map(ExamSession::getId).toList();
        List<ExamAnswer> allAnswers = examAnswerGateway.listBySessionIds(sessionIds);

        // 3. 筛选错题（得分 < 满分）
        List<ExamAnswer> wrongAnswers = allAnswers.stream()
                .filter(a -> a.getEffectiveScore() < a.getMaxScore())
                .toList();

        // 4. 按题型过滤
        if (questionType != null && !questionType.isBlank()) {
            wrongAnswers = wrongAnswers.stream()
                    .filter(a -> questionType.equals(a.getQuestionType()))
                    .toList();
        }

        // 5. 构建场次映射
        Map<Long, ExamSession> sessionMap = sessions.stream()
                .collect(Collectors.toMap(ExamSession::getId, s -> s));

        // 5.1 预读各场次对应试卷的结构化题目行，缓存到 sessionId → (印刷题号 → 题目)
        //     （「出卷即切分」唯一权威来源，替代对 answer_key 自由文本的重复解析）
        Map<Long, Map<Integer, ExamQuestion>> questionBySession = new HashMap<>();
        for (ExamSession session : sessions) {
            questionBySession.put(session.getId(),
                    structuredQuestionSupport.loadByQuestionNumber(session));
        }

        // 6. 解析考生名称
        Map<Long, String> studentNames = resolveStudentNames(
                sessions.stream().map(ExamSession::getStudentId).distinct().toList());

        // 7. 按提交时间倒序组装结果
        List<WrongAnswerVO> result = new ArrayList<>(wrongAnswers.size());
        for (ExamAnswer answer : wrongAnswers) {
            ExamSession session = sessionMap.get(answer.getSessionId());
            if (session == null) {
                continue;
            }
            Map<Integer, ExamQuestion> questionMap =
                    questionBySession.getOrDefault(answer.getSessionId(), Map.of());
            Integer number = answer.getQuestionNumber() != null
                    ? answer.getQuestionNumber() : answer.getQuestionIndex();
            ExamQuestion question = number != null ? questionMap.get(number) : null;
            String studentName = studentNames.getOrDefault(session.getStudentId(), UNKNOWN_STUDENT);
            result.add(WrongAnswerConverter.toVO(answer, session, studentName, question));
        }

        result.sort(Comparator.comparing(
                (WrongAnswerVO vo) -> vo.getSubmitTime() != null ? vo.getSubmitTime() : "",
                Comparator.reverseOrder()));

        logger.debug("查询错题列表 [studentId={}, topic={}, type={}, count={}]",
                studentId, topic, questionType, result.size());

        return result;
    }

    /**
     * 查询已评分的场次（支持按考生和主题过滤）
     */
    private List<ExamSession> findGradedSessions(Long studentId, String topic) {
        List<ExamSession> sessions;
        if (studentId != null) {
            sessions = examSessionGateway.listByStudentIdAndStatuses(studentId, GRADED_STATUSES);
        } else {
            sessions = examSessionGateway.listByStatuses(GRADED_STATUSES);
        }

        // 按主题关键词过滤（应用层）
        if (topic != null && !topic.isBlank()) {
            String lowerTopic = topic.toLowerCase();
            sessions = sessions.stream()
                    .filter(s -> s.getTopic() != null && s.getTopic().toLowerCase().contains(lowerTopic))
                    .toList();
        }

        return sessions;
    }

    /**
     * 批量解析考生名称
     */
    private Map<Long, String> resolveStudentNames(List<Long> studentIds) {
        Map<Long, String> names = new HashMap<>(studentIds.size());
        for (Long id : studentIds) {
            studentGateway.findById(id)
                    .ifPresent(s -> names.put(id,
                            s.getDisplayName() != null ? s.getDisplayName() : s.getUsername()));
        }
        return names;
    }
}
