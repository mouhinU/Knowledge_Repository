package com.mouhin.knowledge.repository.client.dto;

/**
 * 文章生成响应
 *
 * @author Knowledge-Repository
 * @date 2026-09-12
 */
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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getKeyFindings() {
        return keyFindings;
    }

    public void setKeyFindings(String keyFindings) {
        this.keyFindings = keyFindings;
    }

    public String getDraftArticle() {
        return draftArticle;
    }

    public void setDraftArticle(String draftArticle) {
        this.draftArticle = draftArticle;
    }

    public String getReviewFeedback() {
        return reviewFeedback;
    }

    public void setReviewFeedback(String reviewFeedback) {
        this.reviewFeedback = reviewFeedback;
    }

    public int getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(int qualityScore) {
        this.qualityScore = qualityScore;
    }

    public String getFinalArticle() {
        return finalArticle;
    }

    public void setFinalArticle(String finalArticle) {
        this.finalArticle = finalArticle;
    }

    public int getRetrievedChunks() {
        return retrievedChunks;
    }

    public void setRetrievedChunks(int retrievedChunks) {
        this.retrievedChunks = retrievedChunks;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
