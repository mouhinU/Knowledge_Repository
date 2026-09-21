package com.mouhin.knowledge.repository.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 单条错题记录视图对象
 *
 * <p>字段声明顺序严格对齐原 {@code WrongAnswerApplicationService.buildWrongAnswerMap} 的 {@code LinkedHashMap}
 * 插入顺序，确保序列化后 JSON 键序不变。
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

    /**
     * 该题的看图题配图 assetKey 有序数组（源自校对页绑定、按印刷题号回读）。
     *
     * <p>无配图时为 {@code null}，经 {@code @JsonInclude(NON_NULL)} 省略该键，保持既有载荷不变。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> images;
}
