package com.mouhin.knowledge.repository.application.executor.examgeneration.qualitygate;

import com.mouhin.knowledge.repository.domain.model.valueobject.AgentConfidence;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.model.valueobject.QualityGateResult;
import com.mouhin.knowledge.repository.domain.service.QualityGate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 研究阶段质量门禁
 *
 * <p>校验 Research Agent 输出：检索到的知识片段数是否达到最低要求。 不通过时建议重试（重新检索）或跳过（使用 LLM 补充）。
 *
 * @author mouhinU
 * @date 2026-09-28
 */
@Component
public class ResearchQualityGate implements QualityGate {

    private static final String GATE_NAME = "research-min-chunks";

    /** 最低检索结果数（默认 1，允许无结果时走 LLM 补充路径） */
    @Value("${knowledge.blackboard.quality-gate.research.min-chunks:1}")
    private int minChunks;

    /** 低质量阈值（检索结果 < 此值时标记低置信度） */
    @Value("${knowledge.blackboard.quality-gate.research.low-confidence-threshold:3}")
    private int lowConfidenceThreshold;

    @Override
    public QualityGateResult evaluate(BlackboardPhase phase, BlackboardState blackboard) {
        if (phase != BlackboardPhase.RESEARCH) {
            return QualityGateResult.pass(GATE_NAME, "非研究阶段，跳过");
        }

        int chunkCount =
                blackboard.getKnowledgeChunks() != null
                        ? blackboard.getKnowledgeChunks().size()
                        : 0;

        if (chunkCount >= minChunks) {
            AgentConfidence confidence =
                    chunkCount >= lowConfidenceThreshold
                            ? AgentConfidence.high("检索到 " + chunkCount + " 条知识片段")
                            : AgentConfidence.medium(
                                    "检索到 "
                                            + chunkCount
                                            + " 条知识片段（低于理想阈值 "
                                            + lowConfidenceThreshold
                                            + "）");
            return QualityGateResult.pass(
                    GATE_NAME, "检索到 " + chunkCount + " 条知识片段（≥ " + minChunks + "）", confidence);
        }

        // 检索结果不足，建议重试或跳过
        if (chunkCount == 0) {
            return QualityGateResult.fail(
                    GATE_NAME,
                    "知识库无相关结果（0 条），建议走 LLM 补充路径",
                    QualityGateResult.Action.SKIP,
                    AgentConfidence.low("检索结果为空"));
        }

        return QualityGateResult.fail(
                GATE_NAME,
                "检索结果不足（" + chunkCount + " < " + minChunks + "），建议重新检索",
                QualityGateResult.Action.RETRY_CURRENT,
                AgentConfidence.low("检索结果 " + chunkCount + " 条，低于最低要求 " + minChunks));
    }

    @Override
    public String getName() {
        return GATE_NAME;
    }
}
