package com.mouhin.knowledge.repository.application.executor.examreview;

import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 查询单份试卷的结构化题目校对视图执行器（app 层用例，阶段 1-D）
 *
 * <p>逐题返回题干 / 选项 / 答案 / 分值 / 解析，并复跑契约校验给出待修正项， 供管理端就地校对与发布门槛判断。
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
@Component("paperReviewGetQuestionsQryExe")
public class GetPaperQuestionsQryExe {

    private final PaperReviewSupport support;

    @Value("${knowledge.exam.review-required:true}")
    private boolean examReviewRequired;

    public GetPaperQuestionsQryExe(PaperReviewSupport support) {
        this.support = support;
    }

    public PaperQuestionsView execute(String sessionKey) {
        ExamHistory history = support.requireHistory(sessionKey);
        List<ExamQuestion> questions = support.listQuestions(sessionKey);
        ExamPlan plan = support.planOf(history);
        return new PaperQuestionsView(
                history, questions, support.validate(questions, plan), examReviewRequired);
    }
}
