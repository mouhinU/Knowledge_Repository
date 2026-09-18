package com.mouhin.knowledge.repository.application.executor.examtaking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.application.agent.ExamContentRenderAgent;
import com.mouhin.knowledge.repository.application.agent.ExamContentValidatorAgent;
import com.mouhin.knowledge.repository.application.agent.PaperValidationReport;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 在线做题用例共享支撑（app 层）
 * <p>
 * 收敛考生 / 场次鉴权解析、试卷渲染校验、题目 JSON 解析与总分累加等被多个执行器复用的领域协作逻辑。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ExamTakingSupport {

    private static final Logger logger = LoggerFactory.getLogger(ExamTakingSupport.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 卷面总分解析失败时的兜底值 */
    private static final int DEFAULT_TOTAL_SCORE = 100;

    private final StudentGateway studentGateway;
    private final ExamSessionGateway examSessionGateway;
    private final ExamHistoryGateway examHistoryGateway;
    private final ExamQuestionGateway examQuestionGateway;
    private final ExamContentRenderAgent contentRenderAgent;
    private final ExamContentValidatorAgent contentValidatorAgent;

    public ExamTakingSupport(StudentGateway studentGateway,
                             ExamSessionGateway examSessionGateway,
                             ExamHistoryGateway examHistoryGateway,
                             ExamQuestionGateway examQuestionGateway,
                             ExamContentRenderAgent contentRenderAgent,
                             ExamContentValidatorAgent contentValidatorAgent) {
        this.studentGateway = studentGateway;
        this.examSessionGateway = examSessionGateway;
        this.examHistoryGateway = examHistoryGateway;
        this.examQuestionGateway = examQuestionGateway;
        this.contentRenderAgent = contentRenderAgent;
        this.contentValidatorAgent = contentValidatorAgent;
    }

    public Student resolveStudent(String token) {
        return studentGateway.findBySessionToken(token)
                .filter(Student::isTokenValid)
                .orElseThrow(() -> new IllegalArgumentException("登录已过期，请重新登录"));
    }

    public ExamSession resolveSession(String sessionKey, String studentToken) {
        Student student = resolveStudent(studentToken);
        ExamSession session = examSessionGateway.findBySessionKey(sessionKey)
                .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + sessionKey));
        if (!session.getStudentId().equals(student.getId())) {
            throw new IllegalArgumentException("无权访问此考试");
        }
        return session;
    }

    /**
     * 解析本场次对应「试卷」的结构化题目主键（与评分 {@code ExamGradingSupport} 口径一致）。
     * <p>关联出卷历史时取历史 sessionId（生成期切分即以之为键），即时卷则用场次自身 sessionKey。</p>
     *
     * @param session 考试场次
     * @return kb_exam_question 的 session_key
     */
    public String resolvePaperSessionKey(ExamSession session) {
        Long historyId = session.getExamHistoryId();
        if (historyId != null) {
            return examHistoryGateway.findById(historyId)
                    .map(ExamHistory::getSessionId)
                    .filter(s -> s != null && !s.isBlank())
                    .orElse(session.getSessionKey());
        }
        return session.getSessionKey();
    }

    /**
     * 读取本场次试卷的结构化题目行（kb_exam_question，按印刷题号升序）。
     * <p>V2「出卷即切分」下这是题目元数据的权威来源，供答题保存在线匹配题型 / 分值 / 选项，
     * 免去重复解析 markdown。</p>
     *
     * @param session 考试场次
     * @return 结构化题目列表；无行时返回空列表
     */
    public List<ExamQuestion> listPaperQuestions(ExamSession session) {
        String paperKey = resolvePaperSessionKey(session);
        if (paperKey == null || paperKey.isBlank()) {
            return List.of();
        }
        return examQuestionGateway.listBySessionKey(paperKey);
    }

    /** 渲染题目（带考试方案） */
    public String renderWithPlan(String examPaper, String examPlan) {
        return contentRenderAgent.render(examPaper, null, examPlan);
    }

    /** 渲染题目（无考试方案） */
    public String renderNoPlan(String examPaper) {
        return contentRenderAgent.render(examPaper, null);
    }

    public PaperValidationReport validateReport(String questionsJson) {
        return contentValidatorAgent.validate(questionsJson);
    }

    /** 渲染后立即校验，存在阻断性错误时抛出异常阻止开考 */
    public void validateOrThrow(String questionsJson) {
        PaperValidationReport report = contentValidatorAgent.validate(questionsJson);
        if (!report.pass()) {
            String detail = String.join("；", report.errors());
            logger.warn("试卷内容校验未通过，阻止开考：{}", detail);
            throw new IllegalArgumentException("试卷内容校验未通过：" + detail);
        }
    }

    /**
     * 依据渲染后的题目列表累加各题 maxScore，得到与试卷 / 方案一致的卷面总分；解析失败或为 0 回退 100。
     */
    public int sumMaxScore(String questionsJson) {
        try {
            List<Map<String, Object>> qs = OBJECT_MAPPER.readValue(questionsJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            int sum = 0;
            for (Map<String, Object> q : qs) {
                Object ms = q.get("maxScore");
                if (ms instanceof Number num) {
                    sum += num.intValue();
                }
            }
            return sum > 0 ? sum : DEFAULT_TOTAL_SCORE;
        } catch (Exception e) {
            logger.warn("累加题目总分失败，回退为 100：{}", e.getMessage());
            return DEFAULT_TOTAL_SCORE;
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> parseQuestions(String questionsJson) {
        if (questionsJson == null || questionsJson.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(questionsJson, List.class);
        } catch (JsonProcessingException e) {
            logger.warn("解析题目 JSON 失败: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> findQuestion(List<Map<String, Object>> questions, int index) {
        return questions.stream()
                .filter(q -> Integer.valueOf(index).equals(q.get("index")))
                .findFirst()
                .orElse(null);
    }

    /** 持久化场次更新（供 getSession 重解析回写复用） */
    public void updateSession(ExamSession session) {
        examSessionGateway.update(session);
    }

    /**
     * 判断 questionsJson 中的选择题选项是否存在解析异常。
     * <p>如果选择题的 options 数量少于 2 个，说明选项解析失败，需要重新解析。</p>
     */
    public boolean needsReparse(String questionsJson) {
        try {
            List<Map<String, Object>> questions = OBJECT_MAPPER.readValue(questionsJson, List.class);
            for (Map<String, Object> q : questions) {
                String type = (String) q.get("type");
                if ("SINGLE_CHOICE".equals(type) || "MULTI_CHOICE".equals(type)) {
                    Object options = q.get("options");
                    if (options instanceof List<?> optList && optList.size() < 2) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("检查 questionsJson 是否需要重新解析时出错: {}", e.getMessage());
        }
        return false;
    }
}
