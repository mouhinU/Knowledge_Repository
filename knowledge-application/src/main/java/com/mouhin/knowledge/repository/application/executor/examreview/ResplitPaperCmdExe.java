package com.mouhin.knowledge.repository.application.executor.examreview;

import com.mouhin.knowledge.repository.application.executor.examgeneration.ExamQuestionSplitSupport;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 重新切分试卷命令执行器（app 层用例，事务边界，阶段 1-D）
 *
 * <p>按试卷原始 Markdown（exam_paper + answer_key + exam_plan）重新执行「出卷即切分」并幂等回灌 {@code
 * kb_exam_question}，用于管理员修正原文或老数据首次结构化。重切分不改变试卷状态， 发布仍须经 {@link ApprovePaperCmdExe} 通过契约校验。
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
@Component
public class ResplitPaperCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(ResplitPaperCmdExe.class);

    private final PaperReviewSupport support;
    private final ExamQuestionSplitSupport examQuestionSplitSupport;

    public ResplitPaperCmdExe(
            PaperReviewSupport support, ExamQuestionSplitSupport examQuestionSplitSupport) {
        this.support = support;
        this.examQuestionSplitSupport = examQuestionSplitSupport;
    }

    @Transactional
    public ExamQuestionSplitSupport.SplitOutcome execute(String sessionKey) {
        ExamHistory history = support.requireHistory(sessionKey);
        if (history.getExamPaper() == null || history.getExamPaper().isBlank()) {
            throw new IllegalStateException("试卷原文为空，无法重新切分");
        }
        ExamPlan plan = support.planOf(history);
        ExamQuestionSplitSupport.SplitOutcome outcome =
                examQuestionSplitSupport.splitAndPersist(
                        sessionKey, history.getExamPaper(), history.getAnswerKey(), plan);
        logger.info(
                "重新切分完成 [session={}, questions={}, pass={}]",
                sessionKey,
                outcome.count(),
                outcome.validation().pass());
        return outcome;
    }
}
