package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 开始考试请求
 *
 * @author mouhinU
 * @date 2026-09-15
 */
@Getter
@Setter
public class StartExamRequest {

    /** 考生令牌 */
    private String token;

    /** 出卷历史会话 ID（从历史试卷开始考试时使用） */
    private String historySessionId;

    /** 即时试卷 Markdown（即时生成时使用） */
    private String examPaper;

    /** 参考答案 */
    private String answerKey;

    /** 考试主题 */
    private String topic;

    /** 难度 */
    private String difficulty;
}
