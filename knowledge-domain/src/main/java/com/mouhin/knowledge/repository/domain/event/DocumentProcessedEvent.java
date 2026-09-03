package com.mouhin.knowledge.repository.domain.event;

import java.time.LocalDateTime;

/**
 * 文档已处理完成事件（向量化完成，可检索）
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
public record DocumentProcessedEvent(
        String documentKey,
        String fileName,
        int totalPages,
        int totalChunks,
        String ownerId,
        String departmentId,
        LocalDateTime occurredAt
) implements DomainEvent {
}
