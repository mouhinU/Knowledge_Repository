package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import org.springframework.stereotype.Component;

/**
 * 方案异步校验执行器（Node 2 分值检验，SSE 回调耦合）
 *
 * <p>因入参含领域类型，不纳入 client 契约，由适配层在建立 SSE 后直接调用。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class ValidatePlanAsyncCmdExe {

    private final ExamGenerationSupport support;

    public ValidatePlanAsyncCmdExe(ExamGenerationSupport support) {
        this.support = support;
    }

    public void execute(
            String sessionId, ExamPlan plan, BlackboardProgressCallback progressCallback) {
        support.validatePlanAsync(sessionId, plan, progressCallback);
    }
}
