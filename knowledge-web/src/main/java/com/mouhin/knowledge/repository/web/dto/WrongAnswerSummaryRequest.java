package com.mouhin.knowledge.repository.web.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 错题总结请求 DTO
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Getter
@Setter
public class WrongAnswerSummaryRequest {

    /** 考生 ID（可选） */
    private Long studentId;

    /** 主题关键词（可选） */
    private String topic;

    /** 题型（可选） */
    private String questionType;
}
