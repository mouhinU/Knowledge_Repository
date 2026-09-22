package com.mouhin.knowledge.repository.domain.event;

import java.time.LocalDateTime;

/**
 * 文档已创建事件
 *
 * @author mouhinU
 * @date 2026-09-02
 */
public record DocumentCreatedEvent(
        String documentKey,
        String fileName,
        String ownerId,
        String departmentId,
        LocalDateTime occurredAt)
        implements DomainEvent {}
