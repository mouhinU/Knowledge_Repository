package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 结构化题目读取支撑（app 层，纯读领域对象）。
 *
 * <p>「出卷即切分」后 {@code kb_exam_question} 是标准答案 / 解析 / 评分标准的唯一权威来源，
 * 评分链路已纯读该结构。本支撑供下游展示用例（成绩复核详情、错题本）以同一口径纯读结构， 消除对 {@code answer_key} 自由文本的重复解析（双口径残留）。场次与试卷的结构化题目以
 * 出卷会话 {@code session_id} 为键，故先解析出「试卷主键」再按印刷题号建映射。
 *
 * <p>本类只做读取，不触发切分回灌：这些展示用例仅作用于已评分场次，其结构行在评分期 （{@code ExamGradingSupport} 的惰性回灌）即已就绪。
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@Component
public class ExamStructuredQuestionSupport {

    private final ExamHistoryGateway examHistoryGateway;
    private final ExamQuestionGateway examQuestionGateway;

    public ExamStructuredQuestionSupport(
            ExamHistoryGateway examHistoryGateway, ExamQuestionGateway examQuestionGateway) {
        this.examHistoryGateway = examHistoryGateway;
        this.examQuestionGateway = examQuestionGateway;
    }

    /**
     * 解析本场次对应「试卷」的结构化题目主键。
     *
     * <p>关联出卷历史时取历史 sessionId（生成期切分即以之为键），即时卷则用场次自身 sessionKey。
     *
     * @param session 考试场次
     * @return 试卷结构化题目主键，无法解析时返回 null
     */
    public String resolvePaperSessionKey(ExamSession session) {
        if (session == null) {
            return null;
        }
        Long historyId = session.getExamHistoryId();
        if (historyId != null) {
            return examHistoryGateway
                    .findById(historyId)
                    .map(ExamHistory::getSessionId)
                    .filter(s -> s != null && !s.isBlank())
                    .orElse(session.getSessionKey());
        }
        return session.getSessionKey();
    }

    /**
     * 按印刷题号读取本场次对应试卷的结构化题目行（题号 → 题目）。
     *
     * @param session 考试场次
     * @return 印刷题号到结构化题目的有序映射；无结构化行时返回空表
     */
    public Map<Integer, ExamQuestion> loadByQuestionNumber(ExamSession session) {
        Map<Integer, ExamQuestion> map = new LinkedHashMap<>();
        String paperKey = resolvePaperSessionKey(session);
        if (paperKey == null || paperKey.isBlank()) {
            return map;
        }
        List<ExamQuestion> questions = examQuestionGateway.listBySessionKey(paperKey);
        for (ExamQuestion q : questions) {
            if (q.getQuestionNumber() != null) {
                map.put(q.getQuestionNumber(), q);
            }
        }
        return map;
    }
}
