package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 黑板共享状态
 *
 * <p>黑板模式的核心数据结构，所有 Agent 通过读写此对象协作完成文章生成。 每个阶段对应一个数据区域，Agent 只读取前置阶段的输出、写入自己阶段的输出。
 *
 * @author mouhinU
 * @date 2026-09-12
 */
@Getter
public class BlackboardState {

    /** 唯一会话 ID */
    private final String sessionId;

    /** 创建时间 */
    private final Instant createdAt;

    /* ==================== 输入 ==================== */
    /** 当前阶段 */
    private BlackboardPhase phase;

    /** 用户原始问题 */
    private String question;

    /** 用户 ID */
    @Setter private String userId;

    /** 用户部门 ID */
    @Setter private String departmentId;

    /** 用户角色（逗号分隔） */
    @Setter private String roles;

    /* ==================== 研究阶段输出 ==================== */
    /** 是否管理员 */
    @Setter private boolean admin;

    /** 检索到的知识片段 */
    private List<SearchResult> knowledgeChunks = new ArrayList<>();

    /* ==================== 写作阶段输出 ==================== */
    /** 研究员提取的关键发现摘要 */
    private String keyFindings;

    /* ==================== 审核阶段输出 ==================== */
    /** 文章草稿 */
    private String draftArticle;

    /** 审核反馈 */
    private String reviewFeedback;

    /* ==================== 最终输出 ==================== */
    /** 质量评分（0~100，保留两位小数） */
    private double qualityScore;

    /* ==================== 出卷流水线字段 ==================== */
    /** 最终文章 */
    private String finalArticle;

    /** 考试难度（EASY / MEDIUM / HARD） */
    private String examDifficulty;

    /** 题型配置描述（JSON 格式：各题型数量） */
    private String examQuestionConfig;

    /** 学段编码（PRIMARY / JUNIOR / SENIOR，用于分数规则） */
    private String examSchoolLevel;

    /** 目标满分（按学段+科目分数规则计算） */
    private int examTotalScore;

    /** 题型分布方案（多阶段分布 Agent 生成、页面确认后的权威方案，可空） */
    private ExamPlan examPlan;

    /** 分值分配方案（各题型每题分值、小计、总分） */
    private String scoringScheme;

    /** 是否跳过分值校验（Node 2 已校验通过时设为 true，Node 3 的 Scoring Agent 仅写入 scheme 不再校验） */
    @Setter private boolean skipScoringValidation;

    /** 生成的试卷内容（Markdown） */
    private String examPaper;

    /** 标准答案与评分标准 */
    private String answerKey;

    /** 试卷审核反馈 */
    private String examReviewFeedback;

    /** 质量评分六维度明细（JSON：accuracy/wording/coverage/typeReasonable/difficulty/format/total） */
    private String examScoreDetail;

    /** 难度校准评估 */
    private String difficultyAssessment;

    /* ==================== 元数据 ==================== */
    /** 查重去重报告 */
    private String deduplicationReport;

    /** 知识缺口分析报告（标记无知识库依据的题目） */
    private String knowledgeGapReport;

    /** Bloom 认知层级分类结果（每道题的认知层级标注 + 分布统计） */
    private String bloomClassification;

    /** 错题模式分析报告（学生薄弱点 + 个性化复习建议） */
    private String wrongAnswerAnalysis;

    /** 错误信息 */
    private String errorMessage;

    /** 最后更新时间 */
    private Instant updatedAt;

    public BlackboardState(String sessionId, String question) {
        this.sessionId = sessionId;
        this.question = question;
        this.phase = BlackboardPhase.INIT;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** 推进到下一阶段 */
    public void advanceTo(BlackboardPhase nextPhase) {
        this.phase = nextPhase;
        this.updatedAt = Instant.now();
    }

    /** 标记失败 */
    public void markFailed(String error) {
        this.phase = BlackboardPhase.FAILED;
        this.errorMessage = error;
        this.updatedAt = Instant.now();
    }

    // ==================== Getters & Setters ====================

    public void setQuestion(String question) {
        this.question = question;
        this.updatedAt = Instant.now();
    }

    public void setKnowledgeChunks(List<SearchResult> knowledgeChunks) {
        this.knowledgeChunks = knowledgeChunks;
        this.updatedAt = Instant.now();
    }

    public void setKeyFindings(String keyFindings) {
        this.keyFindings = keyFindings;
        this.updatedAt = Instant.now();
    }

    public void setDraftArticle(String draftArticle) {
        this.draftArticle = draftArticle;
        this.updatedAt = Instant.now();
    }

    public void setReviewFeedback(String reviewFeedback) {
        this.reviewFeedback = reviewFeedback;
        this.updatedAt = Instant.now();
    }

    public void setQualityScore(double qualityScore) {
        this.qualityScore = qualityScore;
        this.updatedAt = Instant.now();
    }

    public void setFinalArticle(String finalArticle) {
        this.finalArticle = finalArticle;
        this.updatedAt = Instant.now();
    }

    public void setExamDifficulty(String examDifficulty) {
        this.examDifficulty = examDifficulty;
        this.updatedAt = Instant.now();
    }

    public void setExamQuestionConfig(String examQuestionConfig) {
        this.examQuestionConfig = examQuestionConfig;
        this.updatedAt = Instant.now();
    }

    public void setExamSchoolLevel(String examSchoolLevel) {
        this.examSchoolLevel = examSchoolLevel;
        this.updatedAt = Instant.now();
    }

    public void setExamTotalScore(int examTotalScore) {
        this.examTotalScore = examTotalScore;
        this.updatedAt = Instant.now();
    }

    public void setExamPlan(ExamPlan examPlan) {
        this.examPlan = examPlan;
        this.updatedAt = Instant.now();
    }

    public void setScoringScheme(String scoringScheme) {
        this.scoringScheme = scoringScheme;
        this.updatedAt = Instant.now();
    }

    public void setExamPaper(String examPaper) {
        this.examPaper = examPaper;
        this.updatedAt = Instant.now();
    }

    public void setAnswerKey(String answerKey) {
        this.answerKey = answerKey;
        this.updatedAt = Instant.now();
    }

    public void setExamReviewFeedback(String examReviewFeedback) {
        this.examReviewFeedback = examReviewFeedback;
        this.updatedAt = Instant.now();
    }

    public void setExamScoreDetail(String examScoreDetail) {
        this.examScoreDetail = examScoreDetail;
        this.updatedAt = Instant.now();
    }

    public void setDifficultyAssessment(String difficultyAssessment) {
        this.difficultyAssessment = difficultyAssessment;
        this.updatedAt = Instant.now();
    }

    public void setDeduplicationReport(String deduplicationReport) {
        this.deduplicationReport = deduplicationReport;
        this.updatedAt = Instant.now();
    }

    public void setKnowledgeGapReport(String knowledgeGapReport) {
        this.knowledgeGapReport = knowledgeGapReport;
        this.updatedAt = Instant.now();
    }

    public void setBloomClassification(String bloomClassification) {
        this.bloomClassification = bloomClassification;
        this.updatedAt = Instant.now();
    }

    public void setWrongAnswerAnalysis(String wrongAnswerAnalysis) {
        this.wrongAnswerAnalysis = wrongAnswerAnalysis;
        this.updatedAt = Instant.now();
    }
}
