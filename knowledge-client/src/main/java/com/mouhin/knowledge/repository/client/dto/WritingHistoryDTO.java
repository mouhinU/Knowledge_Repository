package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 写作历史记录视图对象
 *
 * <p>字段集合与顺序与原 {@code WritingHistory} 实体的 Jackson 序列化结果逐一对齐（15 个属性），
 * 保证 {@code /api/agent/article/history} 返回 JSON 键集合、顺序与取值不变。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Getter
@Setter
public class WritingHistoryDTO {

    private Long id;
    private String sessionId;
    private String question;
    private String finalArticle;
    private String draftArticle;
    private Integer qualityScore;
    private Integer retrievedChunks;
    private String keyFindings;
    private String reviewFeedback;
    private String userId;
    private String departmentId;
    private String status;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
