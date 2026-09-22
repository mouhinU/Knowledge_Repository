package com.mouhin.knowledge.repository.application.executor.examgrading;

import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import org.springframework.stereotype.Component;

/**
 * 触发单场考试同步 AI 评分执行器
 *
 * <p>仅对 SUBMITTED 状态的考试生效，由管理端手动触发或定时任务调用。
 *
 * <p>刻意<b>不加</b> {@code @Transactional}：评分含多次（主观题）LLM 流式调用，耗时可达分钟级， 若包在事务内将长时间占用数据库连接并对场次行持有 X
 * 锁，定时任务批量评分时极易耗尽连接池、 且阻塞超时回收 CAS。并发正确性改由评分入口的原子状态机保证—— {@code SUBMITTED→GRADING} CAS 认领 + 每题心跳续约 +
 * 终态 {@code GRADING→AI_GRADED} CAS 落库， 与异步评分链路（本就无事务）保持一致。
 *
 * @author mouhinU
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

    public void execute(Long sessionId) {
        ExamSession session =
                examSessionGateway
                        .findById(sessionId)
                        .orElseThrow(() -> new IllegalArgumentException("考试场次不存在: " + sessionId));

        if (!"SUBMITTED".equals(session.getStatus())) {
            throw new IllegalStateException("仅已交卷的考试可以触发评分，当前状态: " + session.getStatus());
        }

        support.gradeExamInternal(sessionId, null);
    }
}
