package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 触发单场考试同步 AI 评分执行器
 * <p>仅对 SUBMITTED 状态的考试生效，由管理端手动触发或定时任务调用。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class TriggerGradingCmdExe {

    private final ExamSessionGateway examSessionGateway;
    private final ExamGradingSupport support;

    public TriggerGradingCmdExe(ExamSessionGateway examSessionGateway, ExamGradingSupport support) {
        this.examSessionGateway = examSessionGateway;
        this.support = support;
    }

    @Transactional
    public void execute(Long sessionId) {
        ExamSession session = examSessionGateway.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + sessionId));

        if (!"SUBMITTED".equals(session.getStatus())) {
            throw new IllegalStateException("仅已交卷的考试可以触发评分，当前状态: " + session.getStatus());
        }

        support.gradeExamInternal(sessionId, null);
    }
}
