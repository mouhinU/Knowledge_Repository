package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.domain.service.ExamGradingProgressCallback;
import org.springframework.stereotype.Component;

/**
 * 异步评分执行器（回调 / SSE 通道耦合）
 * <p>
 * 因入参携带领域回调 {@link ExamGradingProgressCallback}，不纳入 client 层契约，
 * 由适配层（{@code ExamReviewController}）在建立 SSE 通道后直接调用。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class GradeExamAsyncCmdExe {

    private final ExamGradingSupport support;

    public GradeExamAsyncCmdExe(ExamGradingSupport support) {
        this.support = support;
    }

    public void execute(Long sessionId, ExamGradingProgressCallback callback) {
        support.gradeExamAsync(sessionId, callback);
    }
}
