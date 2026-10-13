package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 单题答题记录实体
 *
 * @author mouhinU
 * @date 2026-09-15
 */
public class ExamAnswer {

    private Long id;

    /** 所属考试场次 ID */
    private Long sessionId;

    /** 题目序号（从 1 开始，答题落库位置序号） */
    private Integer questionIndex;

    /** 印刷题号（与 kb_exam_question.question_number 对齐，权威编号；可为空表示未结构化） */
    private Integer questionNumber;

    /** 题型：SINGLE_CHOICE / MULTI_CHOICE / TRUE_FALSE / FILL_BLANK / SHORT_ANSWER / ESSAY */
    private String questionType;

    /** 题目内容 */
    private String questionContent;

    /** 选项 JSON（选择题/判断题） */
    private String optionsJson;

    /** 满分 */
    private Integer maxScore;

    /** 正确答案 */
    private String correctAnswer;

    /** 学生答案 */
    private String studentAnswer;

    /** 客观题是否正确 */
    private Boolean correct;

    /** AI 评分 */
    private Integer aiScore;

    /** AI 评分反馈 */
    private String aiFeedback;

    /** AI 评分输入（发送给模型的完整 Prompt） */
    private String aiInput;

    /** AI 评分原始输出（模型返回的未解析文本，或客观题的比对依据） */
    private String aiRawOutput;

    /** 人工复核分数 */
    private Integer reviewScore;

    /** 人工复核反馈 */
    private String reviewFeedback;

    /** 复核人 */
    private String reviewedBy;

    /** 复核时间 */
    private LocalDateTime reviewTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== Getters & Setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getQuestionIndex() {
        return questionIndex;
    }

    public void setQuestionIndex(Integer questionIndex) {
        this.questionIndex = questionIndex;
    }

    public Integer getQuestionNumber() {
        return questionNumber;
    }

    public void setQuestionNumber(Integer questionNumber) {
        this.questionNumber = questionNumber;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public String getQuestionContent() {
        return questionContent;
    }

    public void setQuestionContent(String questionContent) {
        this.questionContent = questionContent;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public void setOptionsJson(String optionsJson) {
        this.optionsJson = optionsJson;
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

    public String getStudentAnswer() {
        return studentAnswer;
    }

    public void setStudentAnswer(String studentAnswer) {
        this.studentAnswer = studentAnswer;
    }

    public Boolean getCorrect() {
        return correct;
    }

    public void setCorrect(Boolean correct) {
        this.correct = correct;
    }

    public Integer getAiScore() {
        return aiScore;
    }

    public void setAiScore(Integer aiScore) {
        this.aiScore = aiScore;
    }

    public String getAiFeedback() {
        return aiFeedback;
    }

    public void setAiFeedback(String aiFeedback) {
        this.aiFeedback = aiFeedback;
    }

    public String getAiInput() {
        return aiInput;
    }

    public void setAiInput(String aiInput) {
        this.aiInput = aiInput;
    }

    public String getAiRawOutput() {
        return aiRawOutput;
    }

    public void setAiRawOutput(String aiRawOutput) {
        this.aiRawOutput = aiRawOutput;
    }

    public Integer getReviewScore() {
        return reviewScore;
    }

    public void setReviewScore(Integer reviewScore) {
        this.reviewScore = reviewScore;
    }

    public String getReviewFeedback() {
        return reviewFeedback;
    }

    public void setReviewFeedback(String reviewFeedback) {
        this.reviewFeedback = reviewFeedback;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewTime() {
        return reviewTime;
    }

    public void setReviewTime(LocalDateTime reviewTime) {
        this.reviewTime = reviewTime;
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

    /** 判断是否为客观题（选择题、判断题） */
    public boolean isObjective() {
        return "SINGLE_CHOICE".equals(questionType)
                || "MULTI_CHOICE".equals(questionType)
                || "TRUE_FALSE".equals(questionType);
    }

    /** 判断是否需要人工复核 */
    public boolean needsReview() {
        if (reviewScore != null) {
            return false;
        }
        // 主观题都需要复核
        if (!isObjective()) {
            return true;
        }
        // 客观题 AI 评分与满分有差距时可能需要复核
        return aiScore != null && aiScore < maxScore;
    }

    /** 获取最终得分（人工复核优先，否则 AI 评分，客观题正确则满分） */
    public int getEffectiveScore() {
        if (reviewScore != null) {
            return reviewScore;
        }
        if (aiScore != null) {
            return aiScore;
        }
        if (Boolean.TRUE.equals(correct)) {
            return maxScore;
        }
        return 0;
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
        ExamAnswer that = (ExamAnswer) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
