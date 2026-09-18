package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 结构化题目数据对象
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
@Getter
@Setter
@TableName("kb_exam_question")
public class ExamQuestionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_key")
    private String sessionKey;

    @TableField("question_number")
    private Integer questionNumber;

    @TableField("section_label")
    private String sectionLabel;

    @TableField("question_type")
    private String questionType;

    @TableField("stem")
    private String stem;

    @TableField("options_json")
    private String optionsJson;

    @TableField("blank_count")
    private Integer blankCount;

    @TableField("max_score")
    private Integer maxScore;

    @TableField("correct_answer")
    private String correctAnswer;

    @TableField("analysis")
    private String analysis;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
