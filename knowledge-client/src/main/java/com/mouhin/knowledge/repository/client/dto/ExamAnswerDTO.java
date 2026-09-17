package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 单题答题记录视图对象
 *
 * <p>字段集合与原 {@code ExamAnswer} 实体的 Jackson 序列化结果逐一对齐（22 个属性），
 * 其中 {@code effectiveScore}、{@code objective} 为实体派生 getter 的计算结果，由转换器显式求值后写入，
 * 保证 {@code /api/exam/{sessionKey}/answers} 直接返回该对象的 JSON 键集合与取值不变。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Getter
@Setter
public class ExamAnswerDTO {

    private Long id;
    private Long sessionId;
    private Integer questionIndex;
    private String questionType;
    private String questionContent;
    private String optionsJson;
    private Integer maxScore;
    private String correctAnswer;
    private String studentAnswer;
    private Boolean correct;
    private Integer aiScore;
    private String aiFeedback;
    private String aiInput;
    private String aiRawOutput;
    private Integer reviewScore;
    private String reviewFeedback;
    private String reviewedBy;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean objective;
    private Integer effectiveScore;
}
