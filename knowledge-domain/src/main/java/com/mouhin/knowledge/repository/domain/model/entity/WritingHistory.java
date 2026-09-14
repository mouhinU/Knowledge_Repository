package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * AI 写作历史记录实体
 *
 * @author Knowledge-Repository
 * @date 2026-09-13
 */
public class WritingHistory {

    private Long id;

    /** 会话 ID */
    private String sessionId;

    /** 用户提问 */
    private String question;

    /** 最终文章 */
    private String finalArticle;

    /** 草稿文章（审核前） */
    private String draftArticle;

    /** 质量评分 */
    private Integer qualityScore;

    /** 检索到的知识块数量 */
    private Integer retrievedChunks;

    /** 关键发现 */
    private String keyFindings;

    /** 审核反馈 */
    private String reviewFeedback;

    /** 用户 ID */
    private String userId;

    /** 部门 ID */
    private String departmentId;

    /** 状态：COMPLETED / FAILED */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== Getters & Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getFinalArticle() { return finalArticle; }
    public void setFinalArticle(String finalArticle) { this.finalArticle = finalArticle; }

    public String getDraftArticle() { return draftArticle; }
    public void setDraftArticle(String draftArticle) { this.draftArticle = draftArticle; }

    public Integer getQualityScore() { return qualityScore; }
    public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }

    public Integer getRetrievedChunks() { return retrievedChunks; }
    public void setRetrievedChunks(Integer retrievedChunks) { this.retrievedChunks = retrievedChunks; }

    public String getKeyFindings() { return keyFindings; }
    public void setKeyFindings(String keyFindings) { this.keyFindings = keyFindings; }

    public String getReviewFeedback() { return reviewFeedback; }
    public void setReviewFeedback(String reviewFeedback) { this.reviewFeedback = reviewFeedback; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    // ==================== equals / hashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WritingHistory that = (WritingHistory) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
