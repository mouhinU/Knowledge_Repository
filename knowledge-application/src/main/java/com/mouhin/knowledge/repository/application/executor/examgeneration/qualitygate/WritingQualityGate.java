package com.mouhin.knowledge.repository.application.executor.examgeneration.qualitygate;

import com.mouhin.knowledge.repository.domain.model.valueobject.AgentConfidence;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.model.valueobject.QualityGateResult;
import com.mouhin.knowledge.repository.domain.service.QualityGate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 写作阶段质量门禁
 *
 * <p>校验 Writer Agent 输出：生成的试卷/文章是否非空且达到最低长度要求。 不通过时建议重试（重新生成）。
 *
 * @author mouhinU
 * @date 2026-09-28
 */
@Component
public class WritingQualityGate implements QualityGate {

    private static final String GATE_NAME = "writing-min-length";

    /** 最低输出字符数（默认 50，过短视为无效输出） */
    @Value("${knowledge.blackboard.quality-gate.writing.min-length:50}")
    private int minLength;

    @Override
    public QualityGateResult evaluate(BlackboardPhase phase, BlackboardState blackboard) {
        if (phase != BlackboardPhase.WRITING) {
            return QualityGateResult.pass(GATE_NAME, "非写作阶段，跳过");
        }

        String output =
                blackboard.getExamPaper() != null
                        ? blackboard.getExamPaper()
                        : blackboard.getDraftArticle();

        if (output == null || output.isBlank()) {
            return QualityGateResult.fail(
                    GATE_NAME,
                    "写作输出为空",
                    QualityGateResult.Action.RETRY_CURRENT,
                    AgentConfidence.low("LLM 返回空内容"));
        }

        int length = output.length();
        if (length < minLength) {
            return QualityGateResult.fail(
                    GATE_NAME,
                    "写作输出过短（" + length + " < " + minLength + " 字符）",
                    QualityGateResult.Action.RETRY_CURRENT,
                    AgentConfidence.low("输出长度 " + length + " 字符，低于最低要求 " + minLength));
        }

        AgentConfidence confidence =
                length >= minLength * 3
                        ? AgentConfidence.high("输出 " + length + " 字符，内容充足")
                        : AgentConfidence.medium("输出 " + length + " 字符");

        return QualityGateResult.pass(
                GATE_NAME, "写作输出 " + length + " 字符（≥ " + minLength + "）", confidence);
    }

    @Override
    public String getName() {
        return GATE_NAME;
    }
}
