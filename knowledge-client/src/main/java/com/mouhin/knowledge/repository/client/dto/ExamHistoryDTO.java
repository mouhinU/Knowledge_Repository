package com.mouhin.knowledge.repository.client.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 出卷历史记录视图对象
 *
 * <p>字段集合与顺序与原 {@code ExamHistory} 实体的 Jackson 序列化结果逐一对齐（23 个属性）， 保证 {@code
 * /api/agent/exam/history} 系列端点返回 JSON 键集合、顺序与取值不变。 {@code examPlan} 为序列化后的方案 JSON 字符串（实体即为字符串字段）。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Getter
@Setter
public class ExamHistoryDTO {

    private Long id;
    private String sessionId;
    private String topic;
    private String difficulty;
    private Integer durationMinutes;
    private String questionConfig;
    private String examPlan;
    private String examPaper;
    private String answerKey;
    private Integer qualityScore;
    private String scoreDetail;
    private Integer retrievedChunks;
    private String keyFindings;
    private String reviewFeedback;
    private String difficultyAssessment;
    private String deduplicationReport;
    private String userId;
    private String departmentId;
    private String category;
    private String status;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
