package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import org.springframework.stereotype.Component;

/**
 * 同步出卷执行器（单次 LLM，用于同步出卷 / Word 导出）
 *
 * <p>因入参含领域类型 Permission / ExamPlan，不纳入 client 契约，由适配层直接调用。 保留两条路径：按已确认方案，或按题型数量兜底。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class GenerateExamSyncCmdExe {

    private final ExamGenerationSupport support;

    public GenerateExamSyncCmdExe(ExamGenerationSupport support) {
        this.support = support;
    }

    public String executeWithPlan(
            String topic,
            String difficulty,
            String schoolLevel,
            ExamPlan plan,
            Permission permission,
            String category) {
        return support.generateExam(topic, difficulty, schoolLevel, plan, permission, category);
    }

    public String executeByCounts(
            String topic,
            String difficulty,
            String schoolLevel,
            int singleChoice,
            int multiChoice,
            int trueFalse,
            int fillBlank,
            int shortAnswer,
            int essay,
            Permission permission,
            String category) {
        return support.generateExam(
                topic,
                difficulty,
                schoolLevel,
                singleChoice,
                multiChoice,
                trueFalse,
                fillBlank,
                shortAnswer,
                essay,
                permission,
                category);
    }
}
