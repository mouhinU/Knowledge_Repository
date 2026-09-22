package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 写作历史数据对象
 *
 * @author mouhinU
 * @date 2026-09-13
 */
@Getter
@Setter
@TableName("kb_writing_history")
public class WritingHistoryDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private String sessionId;

    @TableField("question")
    private String question;

    @TableField("final_article")
    private String finalArticle;

    @TableField("draft_article")
    private String draftArticle;

    @TableField("quality_score")
    private Integer qualityScore;

    @TableField("retrieved_chunks")
    private Integer retrievedChunks;

    @TableField("key_findings")
    private String keyFindings;

    @TableField("review_feedback")
    private String reviewFeedback;

    @TableField("user_id")
    private String userId;

    @TableField("department_id")
    private String departmentId;

    @TableField("status")
    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
