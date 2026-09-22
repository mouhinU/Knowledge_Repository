package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import org.springframework.stereotype.Component;

/**
 * 异步出卷流水线执行器（权限 / SSE 回调 / 方案耦合）
 *
 * <p>因入参含领域类型，不纳入 client 契约，由适配层在建立 SSE 后直接调用。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class GenerateExamAsyncCmdExe {

    private final ExamGenerationSupport support;

    public GenerateExamAsyncCmdExe(ExamGenerationSupport support) {
        this.support = support;
    }

    public void execute(
            String topic,
            String difficulty,
            String questionConfig,
            Permission permission,
            BlackboardProgressCallback progressCallback,
            String sessionId,
            String category,
            String schoolLevel,
            ExamPlan plan,
            boolean skipScoringValidation) {
        support.generateExamAsync(
                topic,
                difficulty,
                questionConfig,
                permission,
                progressCallback,
                sessionId,
                category,
                schoolLevel,
                plan,
                skipScoringValidation);
    }
}
