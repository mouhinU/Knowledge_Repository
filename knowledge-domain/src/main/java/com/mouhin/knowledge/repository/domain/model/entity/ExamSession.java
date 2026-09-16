package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 考试场次实体（学生的一次答题记录）
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
public class ExamSession {

    private Long id;

    /**
     * 场次唯一标识（UUID）
     */
    private String sessionKey;

    /**
     * 考生 ID
     */
    private Long studentId;

    /**
     * 关联的出卷历史 ID（可为空，表示即时生成）
     */
    private Long examHistoryId;

    /**
     * 考试主题
     */
    private String topic;

    /**
     * 难度：EASY / MEDIUM / HARD
     */
    private String difficulty;

    /**
     * 考试时长（分钟），为空表示不限时
     */
    private Integer durationMinutes;

    /**
     * 试卷原文（Markdown）
     */
    private String examPaper;

    /**
     * 参考答案
     */
    private String answerKey;

    /**
     * 结构化题目 JSON
     */
    private String questionsJson;

    /**
     * 题型分布方案（ExamPlan JSON，从出卷历史继承），考试端据此按方案渲染题型与分值
     */
    private String examPlan;

    /**
     * 总分
     */
    private Integer totalScore;

    /**
     * AI 评分
     */
    private Integer aiScore;

    /**
     * 最终成绩（人工复核后）
     */
    private Integer finalScore;

    /**
     * 状态：IN_PROGRESS / SUBMITTED / AI_GRADED / REVIEWED / PUBLISHED
     */
    private String status;

    /**
     * 开始答题时间
     */
    private LocalDateTime startTime;

    /**
     * 交卷时间
     */
    private LocalDateTime submitTime;

    /**
     * 评分完成时间
     */
    private LocalDateTime gradeTime;

    /**
     * 成绩发布时间
     */
    private LocalDateTime publishTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== Getters & Setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getExamHistoryId() {
        return examHistoryId;
    }

    public void setExamHistoryId(Long examHistoryId) {
        this.examHistoryId = examHistoryId;
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

    public String getQuestionsJson() {
        return questionsJson;
    }

    public void setQuestionsJson(String questionsJson) {
        this.questionsJson = questionsJson;
    }

    public String getExamPlan() {
        return examPlan;
    }

    public void setExamPlan(String examPlan) {
        this.examPlan = examPlan;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public Integer getAiScore() {
        return aiScore;
    }

    public void setAiScore(Integer aiScore) {
        this.aiScore = aiScore;
    }

    public Integer getFinalScore() {
        return finalScore;
    }

    public void setFinalScore(Integer finalScore) {
        this.finalScore = finalScore;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(LocalDateTime submitTime) {
        this.submitTime = submitTime;
    }

    public LocalDateTime getGradeTime() {
        return gradeTime;
    }

    public void setGradeTime(LocalDateTime gradeTime) {
        this.gradeTime = gradeTime;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
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

    // ==================== 业务方法 ====================

    /**
     * 标记为已提交
     */
    public void submit() {
        this.status = "SUBMITTED";
        this.submitTime = LocalDateTime.now();
    }

    /**
     * 标记为 AI 评分完成
     */
    public void markAiGraded(int aiScore) {
        this.status = "AI_GRADED";
        this.aiScore = aiScore;
        this.gradeTime = LocalDateTime.now();
    }

    /**
     * 标记为人工复核完成
     */
    public void markReviewed(int finalScore) {
        this.status = "REVIEWED";
        this.finalScore = finalScore;
    }

    /**
     * 标记为已发布
     */
    public void markPublished() {
        this.status = "PUBLISHED";
        this.publishTime = LocalDateTime.now();
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
        ExamSession that = (ExamSession) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
