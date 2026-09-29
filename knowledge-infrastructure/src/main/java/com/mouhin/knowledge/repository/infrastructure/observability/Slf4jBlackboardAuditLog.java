package com.mouhin.knowledge.repository.infrastructure.observability;

import com.mouhin.knowledge.repository.domain.gateway.BlackboardAuditLog;
import com.mouhin.knowledge.repository.domain.model.valueobject.AgentConfidence;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基于 SLF4J 的黑板审计日志实现
 *
 * <p>将 Agent 执行元数据写入结构化日志，便于 ELK/Loki 等日志系统采集。 日志格式采用 key=value 键值对，便于解析和检索。
 *
 * @author mouhinU
 * @date 2026-09-28
 */
@Component
@Slf4j
public class Slf4jBlackboardAuditLog implements BlackboardAuditLog {

    private static final String LOG_PREFIX = "[BlackboardAudit]";

    @Override
    public void logAgentStart(
            String sessionId, String agentName, BlackboardPhase phase, String inputSummary) {
        log.info(
                "{} sessionId={} agent={} phase=START inputSummary=\"{}\"",
                LOG_PREFIX,
                sessionId,
                agentName,
                phase,
                sanitize(inputSummary));
    }

    @Override
    public void logAgentComplete(
            String sessionId,
            String agentName,
            BlackboardPhase phase,
            Duration duration,
            Integer tokenCount,
            AgentConfidence confidence,
            String outputSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append(LOG_PREFIX)
                .append(" sessionId=")
                .append(sessionId)
                .append(" agent=")
                .append(agentName)
                .append(" phase=")
                .append(phase)
                .append(" status=COMPLETE")
                .append(" durationMs=")
                .append(duration.toMillis())
                .append(" outputSummary=\"")
                .append(sanitize(outputSummary))
                .append("\"");

        if (tokenCount != null) {
            sb.append(" tokenCount=").append(tokenCount);
        }
        if (confidence != null) {
            sb.append(" confidence=")
                    .append(String.format("%.2f", confidence.getScore()))
                    .append(" confidenceReason=\"")
                    .append(sanitize(confidence.getReason()))
                    .append("\"");
        }

        log.info(sb.toString());
    }

    @Override
    public void logAgentFailed(
            String sessionId,
            String agentName,
            BlackboardPhase phase,
            Duration duration,
            String error) {
        log.error(
                "{} sessionId={} agent={} phase=FAILED durationMs={} error=\"{}\"",
                LOG_PREFIX,
                sessionId,
                agentName,
                phase,
                duration.toMillis(),
                sanitize(error));
    }

    @Override
    public void logQualityGate(
            String sessionId,
            String gateName,
            BlackboardPhase phase,
            boolean passed,
            String reason) {
        log.info(
                "{} sessionId={} gate={} phase={} passed={} reason=\"{}\"",
                LOG_PREFIX,
                sessionId,
                gateName,
                phase,
                passed,
                sanitize(reason));
    }

    @Override
    public void logPipelineComplete(
            String sessionId,
            Duration totalDuration,
            Integer totalTokenCount,
            double finalQualityScore,
            int agentCount) {
        StringBuilder sb = new StringBuilder();
        sb.append(LOG_PREFIX)
                .append(" sessionId=")
                .append(sessionId)
                .append(" phase=PIPELINE_COMPLETE")
                .append(" totalDurationMs=")
                .append(totalDuration.toMillis())
                .append(" finalQualityScore=")
                .append(String.format("%.2f", finalQualityScore))
                .append(" agentCount=")
                .append(agentCount);

        if (totalTokenCount != null) {
            sb.append(" totalTokenCount=").append(totalTokenCount);
        }

        log.info(sb.toString());
    }

    /** 清理日志内容，防止日志注入：先截断再转义，避免转义后长度膨胀导致截断错位 */
    private String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String truncated = text.substring(0, Math.min(text.length(), 200));
        return truncated.replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"");
    }
}
