package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 文章生成响应
 *
 * @author mouhinU
 * @date 2026-09-12
 */
@Getter
@Setter
public class ArticleGenerationResponse {

    /** 会话 ID */
    private String sessionId;

    /** 当前阶段 */
    private String phase;

    /** 研究员的关键发现 */
    private String keyFindings;

    /** 文章草稿 */
    private String draftArticle;

    /** 审核反馈 */
    private String reviewFeedback;

    /** 质量评分（0~100） */
    private int qualityScore;

    /** 最终文章 */
    private String finalArticle;

    /** 检索到的知识片段数 */
    private int retrievedChunks;

    /** 错误信息 */
    private String errorMessage;

    public ArticleGenerationResponse() {}

    public ArticleGenerationResponse(
            String sessionId,
            String phase,
            String keyFindings,
            String draftArticle,
            String reviewFeedback,
            int qualityScore,
            String finalArticle,
            int retrievedChunks,
            String errorMessage) {
        this.sessionId = sessionId;
        this.phase = phase;
        this.keyFindings = keyFindings;
        this.draftArticle = draftArticle;
        this.reviewFeedback = reviewFeedback;
        this.qualityScore = qualityScore;
        this.finalArticle = finalArticle;
        this.retrievedChunks = retrievedChunks;
        this.errorMessage = errorMessage;
    }
}
