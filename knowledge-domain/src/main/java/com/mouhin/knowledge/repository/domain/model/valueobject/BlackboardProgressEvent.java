package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.time.Instant;

/**
 * 黑板进度事件
 * <p>
 * 用于向前端推送各 Agent 的执行进度和中间输出，
 * 支持 SSE（Server-Sent Events）实时刷新。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-13
 */
public class BlackboardProgressEvent {

    /** 事件类型：PHASE / AGENT_OUTPUT / COMPLETED / ERROR */
    private final String type;

    /** 当前阶段 */
    private final BlackboardPhase phase;

    /** Agent 名称（researcher / writer / reviewer） */
    private final String agentName;

    /** Agent 状态（running / done） */
    private final String agentStatus;

    /** Agent 输出内容 */
    private final String output;

    /** 人类可读消息 */
    private final String message;

    /** 最终文章（仅 COMPLETED 事件） */
    private final String finalArticle;

    /** 关键发现（仅 COMPLETED 事件） */
    private final String keyFindings;

    /** 文章草稿（仅 COMPLETED 事件） */
    private final String draftArticle;

    /** 审核反馈（仅 COMPLETED 事件） */
    private final String reviewFeedback;

    /** 质量评分（仅 COMPLETED 事件） */
    private final int qualityScore;

    /** 检索到的知识片段数 */
    private final int retrievedChunks;

    /** 错误信息（仅 ERROR 事件） */
    private final String errorMessage;

    /** 输入物料信息（仅 AGENT_OUTPUT running 事件） */
    private final String materials;

    /** 试卷内容（仅出卷 COMPLETED 事件） */
    private final String examPaper;

    /** 标准答案（仅出卷 COMPLETED 事件） */
    private final String answerKey;

    /** 试卷审核反馈（仅出卷 COMPLETED 事件） */
    private final String examReviewFeedback;

    /** 难度校准评估（仅出卷 COMPLETED 事件） */
    private final String difficultyAssessment;

    /** 查重报告（仅出卷 COMPLETED 事件） */
    private final String deduplicationReport;

    /** 事件时间戳 */
    private final Instant timestamp;

    private BlackboardProgressEvent(Builder builder) {
        this.type = builder.type;
        this.phase = builder.phase;
        this.agentName = builder.agentName;
        this.agentStatus = builder.agentStatus;
        this.output = builder.output;
        this.message = builder.message;
        this.finalArticle = builder.finalArticle;
        this.keyFindings = builder.keyFindings;
        this.draftArticle = builder.draftArticle;
        this.reviewFeedback = builder.reviewFeedback;
        this.qualityScore = builder.qualityScore;
        this.retrievedChunks = builder.retrievedChunks;
        this.errorMessage = builder.errorMessage;
        this.materials = builder.materials;
        this.examPaper = builder.examPaper;
        this.answerKey = builder.answerKey;
        this.examReviewFeedback = builder.examReviewFeedback;
        this.difficultyAssessment = builder.difficultyAssessment;
        this.deduplicationReport = builder.deduplicationReport;
        this.timestamp = Instant.now();
    }

    /**
     * 创建阶段变更事件
     */
    public static BlackboardProgressEvent phaseChanged(BlackboardPhase phase, String message) {
        return new Builder()
                .type("PHASE")
                .phase(phase)
                .message(message)
                .build();
    }

    /**
     * 创建 Agent 状态变更事件（无输出）
     */
    public static BlackboardProgressEvent agentStarted(String agentName, String message) {
        return new Builder()
                .type("AGENT_OUTPUT")
                .agentName(agentName)
                .agentStatus("running")
                .message(message)
                .build();
    }

    /**
     * 创建 Agent 状态变更事件（含输入物料）
     */
    public static BlackboardProgressEvent agentStartedWithMaterials(
            String agentName, String message, String materials) {
        return new Builder()
                .type("AGENT_OUTPUT")
                .agentName(agentName)
                .agentStatus("running")
                .message(message)
                .materials(materials)
                .build();
    }

    /**
     * 创建 Agent 完成事件（含输出）
     */
    public static BlackboardProgressEvent agentCompleted(String agentName, String output) {
        return new Builder()
                .type("AGENT_OUTPUT")
                .agentName(agentName)
                .agentStatus("done")
                .output(output)
                .build();
    }

    /**
     * 创建整体完成事件
     */
    public static BlackboardProgressEvent completed(BlackboardState blackboard, int retrievedChunks) {
        return new Builder()
                .type("COMPLETED")
                .phase(BlackboardPhase.COMPLETED)
                .finalArticle(blackboard.getFinalArticle())
                .keyFindings(blackboard.getKeyFindings())
                .draftArticle(blackboard.getDraftArticle())
                .reviewFeedback(blackboard.getReviewFeedback())
                .qualityScore(blackboard.getQualityScore())
                .retrievedChunks(retrievedChunks)
                .build();
    }

    /**
     * 创建出卷完成事件
     */
    public static BlackboardProgressEvent examCompleted(BlackboardState blackboard, int retrievedChunks) {
        return new Builder()
                .type("COMPLETED")
                .phase(BlackboardPhase.COMPLETED)
                .examPaper(blackboard.getExamPaper())
                .answerKey(blackboard.getAnswerKey())
                .examReviewFeedback(blackboard.getExamReviewFeedback())
                .difficultyAssessment(blackboard.getDifficultyAssessment())
                .deduplicationReport(blackboard.getDeduplicationReport())
                .qualityScore(blackboard.getQualityScore())
                .retrievedChunks(retrievedChunks)
                .build();
    }

    /**
     * 创建错误事件
     */
    public static BlackboardProgressEvent error(String errorMessage) {
        return new Builder()
                .type("ERROR")
                .phase(BlackboardPhase.FAILED)
                .errorMessage(errorMessage)
                .build();
    }

    // ==================== Getters ====================

    public String getType() { return type; }
    public BlackboardPhase getPhase() { return phase; }
    public String getAgentName() { return agentName; }
    public String getAgentStatus() { return agentStatus; }
    public String getOutput() { return output; }
    public String getMessage() { return message; }
    public String getFinalArticle() { return finalArticle; }
    public String getKeyFindings() { return keyFindings; }
    public String getDraftArticle() { return draftArticle; }
    public String getReviewFeedback() { return reviewFeedback; }
    public int getQualityScore() { return qualityScore; }
    public int getRetrievedChunks() { return retrievedChunks; }
    public String getErrorMessage() { return errorMessage; }
    public String getMaterials() { return materials; }
    public String getExamPaper() { return examPaper; }
    public String getAnswerKey() { return answerKey; }
    public String getExamReviewFeedback() { return examReviewFeedback; }
    public String getDifficultyAssessment() { return difficultyAssessment; }
    public String getDeduplicationReport() { return deduplicationReport; }
    public Instant getTimestamp() { return timestamp; }

    /**
     * Builder 模式构建事件
     */
    public static class Builder {
        private String type;
        private BlackboardPhase phase;
        private String agentName;
        private String agentStatus;
        private String output;
        private String message;
        private String finalArticle;
        private String keyFindings;
        private String draftArticle;
        private String reviewFeedback;
        private int qualityScore;
        private int retrievedChunks;
        private String errorMessage;
        private String materials;
        private String examPaper;
        private String answerKey;
        private String examReviewFeedback;
        private String difficultyAssessment;
        private String deduplicationReport;

        public Builder type(String type) { this.type = type; return this; }
        public Builder phase(BlackboardPhase phase) { this.phase = phase; return this; }
        public Builder agentName(String agentName) { this.agentName = agentName; return this; }
        public Builder agentStatus(String agentStatus) { this.agentStatus = agentStatus; return this; }
        public Builder output(String output) { this.output = output; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder finalArticle(String finalArticle) { this.finalArticle = finalArticle; return this; }
        public Builder keyFindings(String keyFindings) { this.keyFindings = keyFindings; return this; }
        public Builder draftArticle(String draftArticle) { this.draftArticle = draftArticle; return this; }
        public Builder reviewFeedback(String reviewFeedback) { this.reviewFeedback = reviewFeedback; return this; }
        public Builder qualityScore(int qualityScore) { this.qualityScore = qualityScore; return this; }
        public Builder retrievedChunks(int retrievedChunks) { this.retrievedChunks = retrievedChunks; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder materials(String materials) { this.materials = materials; return this; }
        public Builder examPaper(String examPaper) { this.examPaper = examPaper; return this; }
        public Builder answerKey(String answerKey) { this.answerKey = answerKey; return this; }
        public Builder examReviewFeedback(String examReviewFeedback) { this.examReviewFeedback = examReviewFeedback; return this; }
        public Builder difficultyAssessment(String difficultyAssessment) { this.difficultyAssessment = difficultyAssessment; return this; }
        public Builder deduplicationReport(String deduplicationReport) { this.deduplicationReport = deduplicationReport; return this; }

        public BlackboardProgressEvent build() {
            return new BlackboardProgressEvent(this);
        }
    }
}
