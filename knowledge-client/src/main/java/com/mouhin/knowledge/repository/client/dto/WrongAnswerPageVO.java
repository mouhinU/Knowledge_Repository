package com.mouhin.knowledge.repository.client.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 错题分页结果视图对象
 *
 * <p>字段顺序对齐原 {@code WrongAnswerApplicationService.pageWrongAnswers}： records、total、page、size、stats。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Getter
@Setter
public class WrongAnswerPageVO {

    private List<WrongAnswerVO> records;
    private Integer total;
    private Integer page;
    private Integer size;
    private WrongAnswerStatsVO stats;
}
