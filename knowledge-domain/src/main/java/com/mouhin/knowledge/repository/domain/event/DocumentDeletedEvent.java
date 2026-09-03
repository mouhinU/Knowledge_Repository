package com.mouhin.knowledge.repository.domain.event;

import java.time.LocalDateTime;

/**
 * 文档已删除事件（需清理 Milvus 中的向量数据）
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
public record DocumentDeletedEvent(
        String documentKey,
        String ownerId,
        LocalDateTime occurredAt
) implements DomainEvent {
}
