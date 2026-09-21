package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.domain.service.ExamGradingProgressCallback;
import org.springframework.stereotype.Component;

/**
 * 异步触发评分执行器（含 SUBMITTED 前置校验 + 回调 / SSE 通道耦合）
 *
 * <p>因入参携带领域回调 {@link ExamGradingProgressCallback}，不纳入 client 层契约， 由适配层在建立 SSE 通道后直接调用。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class TriggerGradingAsyncCmdExe {

    private final ExamGradingSupport support;

    public TriggerGradingAsyncCmdExe(ExamGradingSupport support) {
        this.support = support;
    }

    public void execute(Long sessionId, ExamGradingProgressCallback callback) {
        support.triggerGradingAsync(sessionId, callback);
    }
}
