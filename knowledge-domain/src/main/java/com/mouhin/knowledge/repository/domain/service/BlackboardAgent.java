package com.mouhin.knowledge.repository.domain.service;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;

/**
 * 黑板 Agent 领域服务接口
 * <p>
 * 每个 Agent 负责一个阶段的工作，读取黑板前置阶段的输出，写入本阶段的输出。
 * 执行过程中可通过 progressCallback 推送实时进度事件。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-12
 */
public interface BlackboardAgent {

    /**
     * 执行 Agent 逻辑，读写黑板状态，并通过回调推送进度
     *
     * @param blackboard       共享黑板
     * @param progressCallback 进度回调（可为 null）
     */
    void execute(BlackboardState blackboard, BlackboardProgressCallback progressCallback);

    /**
     * Agent 名称（用于日志和追踪）
     *
     * @return Agent 名称
     */
    String getName();
}
