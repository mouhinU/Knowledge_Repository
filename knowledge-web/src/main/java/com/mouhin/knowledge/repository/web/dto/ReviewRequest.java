package com.mouhin.knowledge.repository.web.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 人工复核请求
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Getter
@Setter
public class ReviewRequest {

    /** 复核分数 */
    private Integer reviewScore;

    /** 复核反馈 */
    private String reviewFeedback;

    /** 复核人 */
    private String reviewer;
}
