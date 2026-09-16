package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * AI 出卷历史记录实体
 *
 * @author Knowledge-Repository
 * @date 2026-09-14
 */
public class ExamHistory {

    private Long id;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 考试主题
     */
    private String topic;

    /**
     * 难度：EASY / MEDIUM / HARD
     */
    private String difficulty;

    /**
     * 考试时长（分钟），AI 根据科目特性设定
     */
    private Integer durationMinutes;

    /**
     * 题型配置描述
     */
    private String questionConfig;

    /**
     * 题型分布方案（ExamPlan 的 JSON 序列化），生成试卷时确认的权威方案，
     * 供考试端按方案渲染题型与分值，保证三处（方案/试卷/渲染）强一致。
     */
    private String examPlan;

    /**
     * 最终试卷
     */
    private String examPaper;

    /**
     * 参考答案
     */
    private String answerKey;

    /**
     * 质量评分
     */
    private Integer qualityScore;

    /**
     * 检索到的知识块数量
     */
    private Integer retrievedChunks;

    /**
     * 关键发现（研究员 Agent）
     */
    private String keyFindings;

    /**
     * 审核反馈（审核 Agent）
     */
    private String reviewFeedback;

    /**
     * 难度评估（校准 Agent）
     */
    private String difficultyAssessment;

    /**
     * 查重报告（去重 Agent）
     */
    private String deduplicationReport;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 部门 ID
     */
    private String departmentId;

    /**
     * 知识分类
     */
    private String category;

    /**
     * 状态：COMPLETED / FAILED
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== Getters & Setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getQuestionConfig() {
        return questionConfig;
    }

    public void setQuestionConfig(String questionConfig) {
        this.questionConfig = questionConfig;
    }

    public String getExamPlan() {
        return examPlan;
    }

    public void setExamPlan(String examPlan) {
        this.examPlan = examPlan;
    }

    public String getExamPaper() {
        return examPaper;
    }

    public void setExamPaper(String examPaper) {
        this.examPaper = examPaper;
    }

    public String getAnswerKey() {
        return answerKey;
    }

    public void setAnswerKey(String answerKey) {
        this.answerKey = answerKey;
    }

    public Integer getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Integer qualityScore) {
        this.qualityScore = qualityScore;
    }

    public Integer getRetrievedChunks() {
        return retrievedChunks;
    }

    public void setRetrievedChunks(Integer retrievedChunks) {
        this.retrievedChunks = retrievedChunks;
    }

    public String getKeyFindings() {
        return keyFindings;
    }

    public void setKeyFindings(String keyFindings) {
        this.keyFindings = keyFindings;
    }

    public String getReviewFeedback() {
        return reviewFeedback;
    }

    public void setReviewFeedback(String reviewFeedback) {
        this.reviewFeedback = reviewFeedback;
    }

    public String getDifficultyAssessment() {
        return difficultyAssessment;
    }

    public void setDifficultyAssessment(String difficultyAssessment) {
        this.difficultyAssessment = difficultyAssessment;
    }

    public String getDeduplicationReport() {
        return deduplicationReport;
    }

    public void setDeduplicationReport(String deduplicationReport) {
        this.deduplicationReport = deduplicationReport;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    // ==================== equals / hashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExamHistory that = (ExamHistory) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
