package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 单条错题记录视图对象
 *
 * <p>字段声明顺序严格对齐原 {@code WrongAnswerApplicationService.buildWrongAnswerMap} 的
 * {@code LinkedHashMap} 插入顺序，确保序列化后 JSON 键序不变。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Getter
@Setter
public class WrongAnswerVO {

    private Long answerId;
    private Long sessionId;
    private String sessionKey;
    private String topic;
    private String difficulty;
    private Long studentId;
    private String studentName;
    private Integer questionIndex;
    private String questionType;
    private String questionContent;
    private String optionsJson;
    private Integer maxScore;
    private Integer effectiveScore;
    private String correctAnswer;
    private String studentAnswer;
    private String aiFeedback;
    private String analysis;
    private String scoringCriteria;
    private Boolean correct;
    private String submitTime;
}
