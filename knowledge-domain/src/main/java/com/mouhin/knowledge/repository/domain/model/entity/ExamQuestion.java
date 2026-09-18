package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 结构化题目实体（出卷即切分产物）
 *
 * <p>试卷生成 / 开考时一次性切分并绑定标准答案，下游评分、展示、错题本均纯读本实体，
 * 不再解析 answer_key 自由文本。权威题号以 {@link #questionNumber}（印刷号，全局连续）为准。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
public class ExamQuestion {

    private Long id;

    /**
     * 所属试卷标识（出卷会话 session_key，同一份卷的多个考生场次共享）
     */
    private String sessionKey;

    /**
     * 印刷题号（试卷上标注的题号，全局连续，权威编号）
     */
    private Integer questionNumber;

    /**
     * 大题标签（如"一、选择题"，用于分组展示，可为空）
     */
    private String sectionLabel;

    /**
     * 题型：SINGLE_CHOICE / MULTI_CHOICE / TRUE_FALSE / FILL_BLANK / SHORT_ANSWER / ESSAY
     */
    private String questionType;

    /**
     * 题干（不含题号与分值标注）
     */
    private String stem;

    /**
     * 选项 JSON（选择题 / 判断题）
     */
    private String optionsJson;

    /**
     * 填空题空数
     */
    private Integer blankCount;

    /**
     * 本题满分
     */
    private Integer maxScore;

    /**
     * 标准答案（切分时从答案键一次性绑定，下游唯一权威来源）
     */
    private String correctAnswer;

    /**
     * 解析 / 说明
     */
    private String analysis;

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

    public Integer getQuestionNumber() {
        return questionNumber;
    }

    public void setQuestionNumber(Integer questionNumber) {
        this.questionNumber = questionNumber;
    }

    public String getSectionLabel() {
        return sectionLabel;
    }

    public void setSectionLabel(String sectionLabel) {
        this.sectionLabel = sectionLabel;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public String getStem() {
        return stem;
    }

    public void setStem(String stem) {
        this.stem = stem;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public void setOptionsJson(String optionsJson) {
        this.optionsJson = optionsJson;
    }

    public Integer getBlankCount() {
        return blankCount;
    }

    public void setBlankCount(Integer blankCount) {
        this.blankCount = blankCount;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
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
     * 判断是否为客观题（选择题、判断题），与 {@link ExamAnswer#isObjective()} 判据保持一致。
     */
    public boolean isObjective() {
        return "SINGLE_CHOICE".equals(questionType)
                || "MULTI_CHOICE".equals(questionType)
                || "TRUE_FALSE".equals(questionType);
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
        ExamQuestion that = (ExamQuestion) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
