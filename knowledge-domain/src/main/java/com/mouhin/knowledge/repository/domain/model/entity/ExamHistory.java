package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * AI 出卷历史记录实体
 *
 * @author mouhinU
 * @date 2026-09-14
 */
public class ExamHistory {

    /** 试卷生命周期状态：已生成待切分/校验（向后兼容旧 COMPLETED 语义，校对前中间态） */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 试卷生命周期状态：出卷契约校验通过，等待人工校对（review-required=true 默认停留于此） */
    public static final String STATUS_REVIEWABLE = "REVIEWABLE";

    /** 试卷生命周期状态：已发布，学生方可开考 */
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 试卷生命周期状态：出卷契约校验未通过，强制人工校对，不可自动发布 */
    public static final String STATUS_VALIDATION_FAILED = "VALIDATION_FAILED";

    /** 生成失败（出卷流水线异常，非试卷质量问题） */
    public static final String STATUS_FAILED = "FAILED";

    /** 试卷生命周期状态：已作废（终态）。作废后学生不可再开考，且从「可用考试」列表移除； 已存在的考试场次随之标注「试卷已作废」，仍可正常显示与查阅，但不再计入有效成绩。 */
    public static final String STATUS_VOIDED = "VOIDED";

    private Long id;

    /** 会话 ID */
    private String sessionId;

    /** 考试主题 */
    private String topic;

    /** 难度：EASY / MEDIUM / HARD */
    private String difficulty;

    /** 考试时长（分钟），AI 根据科目特性设定 */
    private Integer durationMinutes;

    /** 题型配置描述 */
    private String questionConfig;

    /** 题型分布方案（ExamPlan 的 JSON 序列化），生成试卷时确认的权威方案， 供考试端按方案渲染题型与分值，保证三处（方案/试卷/渲染）强一致。 */
    private String examPlan;

    /** 最终试卷 */
    private String examPaper;

    /** 参考答案 */
    private String answerKey;

    /** 质量评分（保留两位小数） */
    private Double qualityScore;

    /** 质量评分六维度明细（JSON：accuracy/wording/coverage/typeReasonable/difficulty/format/total） */
    private String scoreDetail;

    /** 检索到的知识块数量 */
    private Integer retrievedChunks;

    /** 关键发现（研究员 Agent） */
    private String keyFindings;

    /** 审核反馈（审核 Agent） */
    private String reviewFeedback;

    /** 难度评估（校准 Agent） */
    private String difficultyAssessment;

    /** 查重报告（去重 Agent） */
    private String deduplicationReport;

    /** 用户 ID */
    private String userId;

    /** 部门 ID */
    private String departmentId;

    /** 知识分类 */
    private String category;

    /** 试卷生命周期状态：DRAFT / REVIEWABLE / PUBLISHED / VALIDATION_FAILED / FAILED */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 校对审核人（管理员 / 出题人），PUBLISHED 时写入 */
    private String reviewedBy;

    /** 校对审核（发布）时间 */
    private LocalDateTime reviewedTime;

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

    public Double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Double qualityScore) {
        this.qualityScore = qualityScore;
    }

    public String getScoreDetail() {
        return scoreDetail;
    }

    public void setScoreDetail(String scoreDetail) {
        this.scoreDetail = scoreDetail;
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

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewedTime() {
        return reviewedTime;
    }

    public void setReviewedTime(LocalDateTime reviewedTime) {
        this.reviewedTime = reviewedTime;
    }

    // ==================== 业务方法（试卷生命周期） ====================

    /** 是否已发布（学生可开口的唯一判据）。 */
    public boolean isPublished() {
        return STATUS_PUBLISHED.equals(status);
    }

    /** 校验通过，进入待校对状态（review-required=true 默认停留于此）。 */
    public void markReviewable() {
        this.status = STATUS_REVIEWABLE;
    }

    /** 出卷契约校验未通过，强制人工校对，不可自动发布。 */
    public void markValidationFailed() {
        this.status = STATUS_VALIDATION_FAILED;
    }

    /**
     * 校对通过并发布，记录审核人与时间。
     *
     * @param reviewer 审核人（管理员 / 出题人）
     */
    public void markPublished(String reviewer) {
        this.status = STATUS_PUBLISHED;
        this.reviewedBy = reviewer;
        this.reviewedTime = LocalDateTime.now();
    }

    /** 是否已作废（终态）。作废试卷不可再开考，也不出现在可用考试列表。 */
    public boolean isVoided() {
        return STATUS_VOIDED.equals(status);
    }

    /**
     * 作废试卷（终态）。记录操作人与作废时间（复用 reviewedBy / reviewedTime 审计列）。
     *
     * @param operator 作废操作人（管理员 / 出题人）
     */
    public void markVoided(String operator) {
        this.status = STATUS_VOIDED;
        this.reviewedBy = operator;
        this.reviewedTime = LocalDateTime.now();
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
