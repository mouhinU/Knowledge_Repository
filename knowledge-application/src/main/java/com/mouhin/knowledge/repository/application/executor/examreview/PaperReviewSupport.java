package com.mouhin.knowledge.repository.application.executor.examreview;

import com.mouhin.knowledge.repository.application.util.ExamPaperParser;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.service.ExamContractValidator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 试卷校对支撑（app 层，阶段 1-D 校对关口共用逻辑）
 *
 * <p>收敛「按试卷标识加载历史 / 解析方案 / 读取结构化题目 / 复跑契约校验」这几步， 供待校对列表、逐题校对视图、就地编辑回写、批准发布、重切分等执行器复用，
 * 避免各执行器重复装配试卷标识解析与校验流程。
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
@Component
public class PaperReviewSupport {

    private final ExamHistoryGateway examHistoryGateway;
    private final ExamQuestionGateway examQuestionGateway;

    public PaperReviewSupport(
            ExamHistoryGateway examHistoryGateway, ExamQuestionGateway examQuestionGateway) {
        this.examHistoryGateway = examHistoryGateway;
        this.examQuestionGateway = examQuestionGateway;
    }

    /**
     * 按试卷标识加载出卷历史，不存在则抛业务异常。
     *
     * @param sessionKey 试卷标识（出卷会话 session_id）
     * @return 出卷历史
     */
    public ExamHistory requireHistory(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new IllegalArgumentException("试卷标识不能为空");
        }
        return examHistoryGateway
                .findBySessionId(sessionKey)
                .orElseThrow(() -> new IllegalArgumentException("试卷不存在: " + sessionKey));
    }

    /** 解析历史中的题型分布方案（缺省返回 null，校验器据此跳过题数 / 分值对齐）。 */
    public ExamPlan planOf(ExamHistory history) {
        return ExamPaperParser.readPlan(history.getExamPlan());
    }

    /** 读取某份试卷的结构化题目行（按印刷题号升序）。 */
    public List<ExamQuestion> listQuestions(String sessionKey) {
        return examQuestionGateway.listBySessionKey(sessionKey);
    }

    /** 复跑契约校验（不依赖大模型的确定性门禁）。 */
    public ExamContractValidator.Result validate(List<ExamQuestion> questions, ExamPlan plan) {
        return ExamContractValidator.validate(questions, plan);
    }
}
