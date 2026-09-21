package com.mouhin.knowledge.repository.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 单题答题记录视图对象
 *
 * <p>字段集合与原 {@code ExamAnswer} 实体的 Jackson 序列化结果逐一对齐（22 个属性）， 其中 {@code effectiveScore}、{@code
 * objective} 为实体派生 getter 的计算结果，由转换器显式求值后写入， 保证 {@code /api/exam/{sessionKey}/answers} 直接返回该对象的
 * JSON 键集合与取值不变。
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

    /**
     * 该题的看图题配图 assetKey 有序数组（按印刷题号从结构化题目行回读），供教师「成绩复核详情」展示。
     *
     * <p>学生端 {@code /api/exam/{sessionKey}/answers} 不解析配图时为 {@code null}， 经
     * {@code @JsonInclude(NON_NULL)} 省略该键，保持既有载荷字节不变。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> images;
}
