package com.mouhin.knowledge.repository.application.executor.examreview;

import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.service.ExamContractValidator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 就地编辑单题校对信息命令执行器（app 层用例，事务边界，阶段 1-D）
 *
 * <p>更新某题的标准答案 / 解析 / 分值后回写 {@code kb_exam_question}，并复跑契约校验返回最新问题清单，
 * 供前端即时反馈是否已满足发布条件。不自动改变试卷状态（发布须由 {@link ApprovePaperCmdExe} 触发）。
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
@Component
public class UpdatePaperQuestionCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(UpdatePaperQuestionCmdExe.class);

    private final PaperReviewSupport support;
    private final ExamQuestionGateway examQuestionGateway;

    public UpdatePaperQuestionCmdExe(
            PaperReviewSupport support, ExamQuestionGateway examQuestionGateway) {
        this.support = support;
        this.examQuestionGateway = examQuestionGateway;
    }

    @Transactional
    public ExamContractValidator.Result execute(
            String sessionKey,
            Integer questionNumber,
            String correctAnswer,
            String analysis,
            Integer maxScore) {
        if (questionNumber == null) {
            throw new IllegalArgumentException("题号不能为空");
        }
        ExamHistory history = support.requireHistory(sessionKey);
        examQuestionGateway.updateCorrection(
                sessionKey, questionNumber, correctAnswer, analysis, maxScore);
        logger.info("校对就地编辑 [session={}, number={}]", sessionKey, questionNumber);

        List<ExamQuestion> questions = support.listQuestions(sessionKey);
        ExamPlan plan = support.planOf(history);
        return support.validate(questions, plan);
    }
}
