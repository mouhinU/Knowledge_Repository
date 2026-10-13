package com.mouhin.knowledge.repository.application.executor.examreview;

import com.mouhin.knowledge.repository.application.executor.examgeneration.ExamQuestionSplitSupport;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.service.ExamContractValidator;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 校对通过并发布命令执行器（app 层用例，事务边界，阶段 1-D 发布门禁）
 *
 * <p>发布前强制复跑出卷契约校验：通过则把试卷状态置 {@code PUBLISHED} 并记录审核人 / 时间，学生方可开考；
 * 未通过则抛业务异常并阻断发布（校验不过不可绕过）。若结构化题目行为空（如历史遗留卷），先惰性执行 出卷即切分回灌后再校验。
 *
 * @author mouhinU
 * @date 2026-09-18
 */
@Component
@Slf4j
public class ApprovePaperCmdExe {

    private final PaperReviewSupport support;
    private final ExamHistoryGateway examHistoryGateway;
    private final ExamQuestionSplitSupport examQuestionSplitSupport;

    public ApprovePaperCmdExe(
            PaperReviewSupport support,
            ExamHistoryGateway examHistoryGateway,
            ExamQuestionSplitSupport examQuestionSplitSupport) {
        this.support = support;
        this.examHistoryGateway = examHistoryGateway;
        this.examQuestionSplitSupport = examQuestionSplitSupport;
    }

    @Transactional
    public ExamContractValidator.Result execute(String sessionKey, String reviewer) {
        ExamHistory history = support.requireHistory(sessionKey);
        if (ExamHistory.STATUS_FAILED.equals(history.getStatus())) {
            throw new IllegalStateException("试卷生成失败，无法发布");
        }

        ExamPlan plan = support.planOf(history);
        List<ExamQuestion> questions = support.listQuestions(sessionKey);
        if (questions.isEmpty()) {
            // 惰性回灌：历史遗留 / 切分缺失的卷，先执行出卷即切分再校验
            log.info("校对发布前题目行为空，执行惰性回灌 [session={}]", sessionKey);
            examQuestionSplitSupport.splitAndPersist(
                    sessionKey, history.getExamPaper(), history.getAnswerKey(), plan);
            questions = support.listQuestions(sessionKey);
        }

        ExamContractValidator.Result validation = support.validate(questions, plan);
        if (!validation.pass()) {
            log.warn("校对发布被拒：契约校验未通过 [session={}, issues={}]", sessionKey, validation.issues());
            return validation;
        }

        String actor = (reviewer != null && !reviewer.isBlank()) ? reviewer : "admin";
        history.markPublished(actor);
        history.setUpdateTime(LocalDateTime.now());
        examHistoryGateway.update(history);
        log.info("试卷已校对通过并发布 [session={}, reviewer={}]", sessionKey, actor);
        return validation;
    }
}
