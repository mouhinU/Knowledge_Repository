package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.mouhin.knowledge.repository.domain.gateway.BlackboardAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.BlackboardMetrics;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 装饰器：透明包装 {@link BlackboardAgent}，自动采集审计日志与 Micrometer 指标。
 *
 * <p>装饰器在 Agent 执行前后记录：开始/完成/失败审计事件、执行耗时、执行计数、质量评分。 对原 Agent 逻辑零侵入，仅增加横切可观测能力。
 *
 * <p>用法：在编排层构造器中以 {@code decorator.wrap(agent)} 包装注入的 Agent Bean。
 *
 * @author mouhinU
 * @date 2026-09-28
 */
@Slf4j
public class BlackboardAgentDecorator implements BlackboardAgent {

    private final BlackboardAgent delegate;
    private final BlackboardAuditLog auditLog;
    private final BlackboardMetrics metrics;

    public BlackboardAgentDecorator(
            BlackboardAgent delegate, BlackboardAuditLog auditLog, BlackboardMetrics metrics) {
        this.delegate = delegate;
        this.auditLog = auditLog;
        this.metrics = metrics;
    }

    @Override
    public void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback) {
        String agentName = delegate.getName();
        BlackboardPhase phase = blackboard.getPhase();
        Instant start = Instant.now();

        auditLog.logAgentStart(
                blackboard.getSessionId(), agentName, phase, summarizeInput(blackboard));
        metrics.incrementAgentCount(agentName, phase, "STARTED");

        try {
            delegate.execute(blackboard, progressCallback);

            Duration duration = Duration.between(start, Instant.now());
            auditLog.logAgentComplete(
                    blackboard.getSessionId(),
                    agentName,
                    blackboard.getPhase(),
                    duration,
                    null,
                    null,
                    summarizeOutput(blackboard));
            metrics.recordAgentDuration(agentName, blackboard.getPhase(), duration);
            metrics.incrementAgentCount(agentName, blackboard.getPhase(), "COMPLETE");
        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            auditLog.logAgentFailed(
                    blackboard.getSessionId(), agentName, phase, duration, e.getMessage());
            metrics.incrementAgentCount(agentName, phase, "FAILED");
            throw e;
        }
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    /** 返回被装饰的原始 Agent（测试 / 高级场景可用）。 */
    public BlackboardAgent getDelegate() {
        return delegate;
    }

    /** 摘要输入内容（取问题前 100 字符），防止审计日志过长。 */
    private static String summarizeInput(BlackboardState blackboard) {
        String q = blackboard.getQuestion();
        if (q == null) {
            return "";
        }
        return q.length() <= 100 ? q : q.substring(0, 100) + "...";
    }

    /** 摘要输出内容（取当前阶段关键产出前 100 字符）。 */
    private static String summarizeOutput(BlackboardState blackboard) {
        // 优先取考试流水线产出，其次取文章流水线产出
        String paper = blackboard.getExamPaper();
        if (paper != null && !paper.isBlank()) {
            return truncate(paper);
        }
        String article = blackboard.getDraftArticle();
        if (article != null && !article.isBlank()) {
            return truncate(article);
        }
        String findings = blackboard.getKeyFindings();
        if (findings != null && !findings.isBlank()) {
            return truncate(findings);
        }
        return "";
    }

    private static String truncate(String text) {
        return text.length() <= 100 ? text : text.substring(0, 100) + "...";
    }
}
