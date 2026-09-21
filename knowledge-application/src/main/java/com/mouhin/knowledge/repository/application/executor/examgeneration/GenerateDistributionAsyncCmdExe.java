package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import org.springframework.stereotype.Component;

/**
 * 异步（流式）生成题型分布方案执行器（权限 / SSE 回调耦合）
 *
 * <p>因入参含领域类型，不纳入 client 契约，由适配层在建立 SSE 后直接调用。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class GenerateDistributionAsyncCmdExe {

    private final ExamGenerationSupport support;

    public GenerateDistributionAsyncCmdExe(ExamGenerationSupport support) {
        this.support = support;
    }

    public void execute(
            String sessionId,
            String topic,
            String difficulty,
            String schoolLevel,
            String category,
            Permission permission,
            BlackboardProgressCallback progressCallback) {
        support.generateDistributionAsync(
                sessionId, topic, difficulty, schoolLevel, category, permission, progressCallback);
    }
}
