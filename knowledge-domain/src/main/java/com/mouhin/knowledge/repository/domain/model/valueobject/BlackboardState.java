package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 黑板共享状态
 * <p>
 * 黑板模式的核心数据结构，所有 Agent 通过读写此对象协作完成文章生成。
 * 每个阶段对应一个数据区域，Agent 只读取前置阶段的输出、写入自己阶段的输出。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-12
 */
public class BlackboardState {

    /**
     * 唯一会话 ID
     */
    private final String sessionId;
    /**
     * 创建时间
     */
    private final Instant createdAt;

    /* ==================== 输入 ==================== */
    /**
     * 当前阶段
     */
    private BlackboardPhase phase;
    /**
     * 用户原始问题
     */
    private String question;
    /**
     * 用户 ID
     */
    private String userId;
    /**
     * 用户部门 ID
     */
    private String departmentId;
    /**
     * 用户角色（逗号分隔）
     */
    private String roles;

    /* ==================== 研究阶段输出 ==================== */
    /**
     * 是否管理员
     */
    private boolean admin;
    /**
     * 检索到的知识片段
     */
    private List<SearchResult> knowledgeChunks = new ArrayList<>();

    /* ==================== 写作阶段输出 ==================== */
    /**
     * 研究员提取的关键发现摘要
     */
    private String keyFindings;

    /* ==================== 审核阶段输出 ==================== */
    /**
     * 文章草稿
     */
    private String draftArticle;
    /**
     * 审核反馈
     */
    private String reviewFeedback;

    /* ==================== 最终输出 ==================== */
    /**
     * 质量评分（0~100）
     */
    private int qualityScore;

    /* ==================== 出卷流水线字段 ==================== */
    /**
     * 最终文章
     */
    private String finalArticle;
    /**
     * 考试难度（EASY / MEDIUM / HARD）
     */
    private String examDifficulty;
    /**
     * 题型配置描述（JSON 格式：各题型数量）
     */
    private String examQuestionConfig;
    /**
     * 学段编码（PRIMARY / JUNIOR / SENIOR，用于分数规则）
     */
    private String examSchoolLevel;
    /**
     * 目标满分（按学段+科目分数规则计算）
     */
    private int examTotalScore;
    /**
     * 题型分布方案（多阶段分布 Agent 生成、页面确认后的权威方案，可空）
     */
    private ExamPlan examPlan;
    /**
     * 分值分配方案（各题型每题分值、小计、总分）
     */
    private String scoringScheme;
    /**
     * 是否跳过分值校验（Node 2 已校验通过时设为 true，Node 3 的 Scoring Agent 仅写入 scheme 不再校验）
     */
    private boolean skipScoringValidation;
    /**
     * 生成的试卷内容（Markdown）
     */
    private String examPaper;
    /**
     * 标准答案与评分标准
     */
    private String answerKey;
    /**
     * 试卷审核反馈
     */
    private String examReviewFeedback;
    /**
     * 质量评分六维度明细（JSON：accuracy/wording/coverage/typeReasonable/difficulty/format/total）
     */
    private String examScoreDetail;
    /**
     * 难度校准评估
     */
    private String difficultyAssessment;

    /* ==================== 元数据 ==================== */
    /**
     * 查重去重报告
     */
    private String deduplicationReport;
    /**
     * 错误信息
     */
    private String errorMessage;
    /**
     * 最后更新时间
     */
    private Instant updatedAt;

    public BlackboardState(String sessionId, String question) {
        this.sessionId = sessionId;
        this.question = question;
        this.phase = BlackboardPhase.INIT;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * 推进到下一阶段
     */
    public void advanceTo(BlackboardPhase nextPhase) {
        this.phase = nextPhase;
        this.updatedAt = Instant.now();
    }

    /**
     * 标记失败
     */
    public void markFailed(String error) {
        this.phase = BlackboardPhase.FAILED;
        this.errorMessage = error;
        this.updatedAt = Instant.now();
    }

    // ==================== Getters & Setters ====================

    public String getSessionId() {
        return sessionId;
    }

    public BlackboardPhase getPhase() {
        return phase;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
        this.updatedAt = Instant.now();
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

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public List<SearchResult> getKnowledgeChunks() {
        return knowledgeChunks;
    }

    public void setKnowledgeChunks(List<SearchResult> knowledgeChunks) {
        this.knowledgeChunks = knowledgeChunks;
        this.updatedAt = Instant.now();
    }

    public String getKeyFindings() {
        return keyFindings;
    }

    public void setKeyFindings(String keyFindings) {
        this.keyFindings = keyFindings;
        this.updatedAt = Instant.now();
    }

    public String getDraftArticle() {
        return draftArticle;
    }

    public void setDraftArticle(String draftArticle) {
        this.draftArticle = draftArticle;
        this.updatedAt = Instant.now();
    }

    public String getReviewFeedback() {
        return reviewFeedback;
    }

    public void setReviewFeedback(String reviewFeedback) {
        this.reviewFeedback = reviewFeedback;
        this.updatedAt = Instant.now();
    }

    public int getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(int qualityScore) {
        this.qualityScore = qualityScore;
        this.updatedAt = Instant.now();
    }

    public String getFinalArticle() {
        return finalArticle;
    }

    public void setFinalArticle(String finalArticle) {
        this.finalArticle = finalArticle;
        this.updatedAt = Instant.now();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getExamDifficulty() {
        return examDifficulty;
    }

    public void setExamDifficulty(String examDifficulty) {
        this.examDifficulty = examDifficulty;
        this.updatedAt = Instant.now();
    }

    public String getExamQuestionConfig() {
        return examQuestionConfig;
    }

    public void setExamQuestionConfig(String examQuestionConfig) {
        this.examQuestionConfig = examQuestionConfig;
        this.updatedAt = Instant.now();
    }

    public String getExamSchoolLevel() {
        return examSchoolLevel;
    }

    public void setExamSchoolLevel(String examSchoolLevel) {
        this.examSchoolLevel = examSchoolLevel;
        this.updatedAt = Instant.now();
    }

    public int getExamTotalScore() {
        return examTotalScore;
    }

    public void setExamTotalScore(int examTotalScore) {
        this.examTotalScore = examTotalScore;
        this.updatedAt = Instant.now();
    }

    public ExamPlan getExamPlan() {
        return examPlan;
    }

    public void setExamPlan(ExamPlan examPlan) {
        this.examPlan = examPlan;
        this.updatedAt = Instant.now();
    }

    public String getScoringScheme() {
        return scoringScheme;
    }

    public void setScoringScheme(String scoringScheme) {
        this.scoringScheme = scoringScheme;
        this.updatedAt = Instant.now();
    }

    public boolean isSkipScoringValidation() {
        return skipScoringValidation;
    }

    public void setSkipScoringValidation(boolean skipScoringValidation) {
        this.skipScoringValidation = skipScoringValidation;
    }

    public String getExamPaper() {
        return examPaper;
    }

    public void setExamPaper(String examPaper) {
        this.examPaper = examPaper;
        this.updatedAt = Instant.now();
    }

    public String getAnswerKey() {
        return answerKey;
    }

    public void setAnswerKey(String answerKey) {
        this.answerKey = answerKey;
        this.updatedAt = Instant.now();
    }

    public String getExamReviewFeedback() {
        return examReviewFeedback;
    }

    public void setExamReviewFeedback(String examReviewFeedback) {
        this.examReviewFeedback = examReviewFeedback;
        this.updatedAt = Instant.now();
    }

    public String getExamScoreDetail() {
        return examScoreDetail;
    }

    public void setExamScoreDetail(String examScoreDetail) {
        this.examScoreDetail = examScoreDetail;
        this.updatedAt = Instant.now();
    }

    public String getDifficultyAssessment() {
        return difficultyAssessment;
    }

    public void setDifficultyAssessment(String difficultyAssessment) {
        this.difficultyAssessment = difficultyAssessment;
        this.updatedAt = Instant.now();
    }

    public String getDeduplicationReport() {
        return deduplicationReport;
    }

    public void setDeduplicationReport(String deduplicationReport) {
        this.deduplicationReport = deduplicationReport;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
