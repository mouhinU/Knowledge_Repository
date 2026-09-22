package com.mouhin.knowledge.repository.client.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 考试场次视图对象
 *
 * <p>字段集合与原 {@code ExamSession} 实体的 Jackson 序列化结果逐一对齐（21 个属性， 顺序一致），保证 {@code
 * /api/exam/my-sessions} 等直接返回该对象的端点 JSON 不变。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Getter
@Setter
public class ExamSessionDTO {

    private Long id;
    private String sessionKey;
    private Long studentId;
    private Long examHistoryId;
    private String topic;
    private String difficulty;
    private Integer durationMinutes;
    private String examPaper;
    private String answerKey;
    private String questionsJson;
    private String examPlan;
    private Integer totalScore;
    private Integer aiScore;
    private Integer finalScore;
    private String status;

    /** 对应 AI 试卷是否已被教师作废；true 时前端标注「试卷已作废」。 */
    private boolean voided;

    private LocalDateTime startTime;
    private LocalDateTime submitTime;
    private LocalDateTime gradeTime;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
