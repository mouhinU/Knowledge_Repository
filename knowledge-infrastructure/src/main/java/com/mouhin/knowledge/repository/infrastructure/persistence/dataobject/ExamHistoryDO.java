package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 出卷历史数据对象
 *
 * @author Knowledge-Repository
 * @date 2026-09-14
 */
@Getter
@Setter
@TableName("kb_exam_history")
public class ExamHistoryDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private String sessionId;

    @TableField("topic")
    private String topic;

    @TableField("difficulty")
    private String difficulty;

    @TableField("duration_minutes")
    private Integer durationMinutes;

    @TableField("question_config")
    private String questionConfig;

    @TableField("exam_plan")
    private String examPlan;

    @TableField("exam_paper")
    private String examPaper;

    @TableField("answer_key")
    private String answerKey;

    @TableField("quality_score")
    private Integer qualityScore;

    @TableField("score_detail")
    private String scoreDetail;

    @TableField("retrieved_chunks")
    private Integer retrievedChunks;

    @TableField("key_findings")
    private String keyFindings;

    @TableField("review_feedback")
    private String reviewFeedback;

    @TableField("difficulty_assessment")
    private String difficultyAssessment;

    @TableField("deduplication_report")
    private String deduplicationReport;

    @TableField("user_id")
    private String userId;

    @TableField("department_id")
    private String departmentId;

    @TableField("category")
    private String category;

    @TableField("status")
    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField("reviewed_by")
    private String reviewedBy;

    @TableField("reviewed_time")
    private LocalDateTime reviewedTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
