package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 考试场次数据对象
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Getter
@Setter
@TableName("kb_exam_session")
public class ExamSessionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_key")
    private String sessionKey;

    @TableField("student_id")
    private Long studentId;

    @TableField("exam_history_id")
    private Long examHistoryId;

    @TableField("topic")
    private String topic;

    @TableField("difficulty")
    private String difficulty;

    @TableField("duration_minutes")
    private Integer durationMinutes;

    @TableField("exam_paper")
    private String examPaper;

    @TableField("answer_key")
    private String answerKey;

    @TableField("questions_json")
    private String questionsJson;

    @TableField("exam_plan")
    private String examPlan;

    @TableField("total_score")
    private Integer totalScore;

    @TableField("ai_score")
    private Integer aiScore;

    @TableField("final_score")
    private Integer finalScore;

    @TableField("status")
    private String status;

    /**
     * 评分围栏令牌（CONC-1）：认领 GRADING 时写入的一次性 UUID，
     * 心跳 / 终态 / 回退均以 status==GRADING 且本列匹配为谓词，防止超时回收后旧评分者交叉写。
     * 属基础设施并发控制关注点，不进入领域实体，仅经 Gateway 的 LambdaUpdateWrapper 读写。
     */
    @TableField("grading_token")
    private String gradingToken;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("submit_time")
    private LocalDateTime submitTime;

    @TableField("grade_time")
    private LocalDateTime gradeTime;

    @TableField("publish_time")
    private LocalDateTime publishTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
