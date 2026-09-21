package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 错题统计概览视图对象
 *
 * <p>字段顺序对齐原 {@code WrongAnswerApplicationService.buildStats}：
 * totalCount、topicCount、studentCount、topType（可为 null）、avgScoreRate（保留 4 位小数）。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Getter
@Setter
public class WrongAnswerStatsVO {

    private Integer totalCount;
    private Long topicCount;
    private Long studentCount;
    private String topType;
    private Double avgScoreRate;
}
