package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 知识库统计视图对象
 *
 * <p>字段与原 {@code DocumentAdminController.getStats} 返回的 Map 一致：
 * totalDocuments、indexedDocuments、processingDocuments、failedDocuments。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Getter
@Setter
public class KnowledgeStatsVO {

    private Long totalDocuments;
    private Long indexedDocuments;
    private Long processingDocuments;
    private Long failedDocuments;
}
