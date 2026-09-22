package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 单题答题记录数据对象
 *
 * @author mouhinU
 * @date 2026-09-15
 */
@Getter
@Setter
@TableName("kb_exam_answer")
public class ExamAnswerDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    @TableField("question_index")
    private Integer questionIndex;

    @TableField("question_number")
    private Integer questionNumber;

    @TableField("question_type")
    private String questionType;

    @TableField("question_content")
    private String questionContent;

    @TableField("options_json")
    private String optionsJson;

    @TableField("max_score")
    private Integer maxScore;

    @TableField("correct_answer")
    private String correctAnswer;

    @TableField("student_answer")
    private String studentAnswer;

    @TableField("is_correct")
    private Boolean correct;

    @TableField("ai_score")
    private Integer aiScore;

    @TableField("ai_feedback")
    private String aiFeedback;

    @TableField("ai_input")
    private String aiInput;

    @TableField("ai_raw_output")
    private String aiRawOutput;

    @TableField("review_score")
    private Integer reviewScore;

    @TableField("review_feedback")
    private String reviewFeedback;

    @TableField("reviewed_by")
    private String reviewedBy;

    @TableField("review_time")
    private LocalDateTime reviewTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
