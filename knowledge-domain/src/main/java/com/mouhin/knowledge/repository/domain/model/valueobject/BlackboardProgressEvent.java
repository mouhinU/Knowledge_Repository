package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.time.Instant;

/**
 * 黑板进度事件
 *
 * <p>用于向前端推送各 Agent 的执行进度和中间输出， 支持 SSE（Server-Sent Events）实时刷新。
 *
 * @author mouhinU
 * @date 2026-09-13
 */
public class BlackboardProgressEvent {

    /** 事件类型：PHASE / AGENT_OUTPUT / AGENT_TOKEN / COMPLETED / ERROR */
    private final String type;

    /** 当前阶段 */
    private final BlackboardPhase phase;

    /** Agent 名称（researcher / writer / reviewer） */
    private final String agentName;

    /** Agent 状态（running / done / failed） */
    private final String agentStatus;

    /** Token 增量类型（仅 AGENT_TOKEN 事件）：output（正式输出）/ thinking（思考链） */
    private final String kind;

    /** Token 增量内容（仅 AGENT_TOKEN 事件） */
    private final String delta;

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

    /** 题型分布方案 JSON（仅分布 COMPLETED 事件） */
    private final String distributionPlan;

    /** 当前生成轮次（1-based，仅出卷重试轮的 PHASE 事件携带；首轮为 null 表示不显示） */
    private final Integer round;

    /** 最大生成轮次（含首轮的总轮数，仅出卷重试轮的 PHASE 事件携带） */
    private final Integer maxRound;

    /** 事件时间戳 */
    private final Instant timestamp;

    private BlackboardProgressEvent(Builder builder) {
        this.type = builder.type;
        this.phase = builder.phase;
        this.agentName = builder.agentName;
        this.agentStatus = builder.agentStatus;
        this.kind = builder.kind;
        this.delta = builder.delta;
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
        this.distributionPlan = builder.distributionPlan;
        this.round = builder.round;
        this.maxRound = builder.maxRound;
        this.timestamp = Instant.now();
    }

    /** 创建阶段变更事件 */
    public static BlackboardProgressEvent phaseChanged(BlackboardPhase phase, String message) {
        return new Builder().type("PHASE").phase(phase).message(message).build();
    }

    /**
     * 创建"改进重试轮"阶段事件：在出卷流水线因质量分不达标触发重新生成时下发， 携带当前轮次（1-based）与总轮数，供前端显示"第 N / 共 M 轮"。
     *
     * @param phase 当前阶段（通常为 WRITING，表示重新开始编写）
     * @param message 人类可读提示
     * @param round 当前轮次（1-based）
     * @param maxRound 总轮数（含首轮）
     * @return 阶段事件
     */
    public static BlackboardProgressEvent retryRoundChanged(
            BlackboardPhase phase, String message, int round, int maxRound) {
        return new Builder()
                .type("PHASE")
                .phase(phase)
                .message(message)
                .round(round)
                .maxRound(maxRound)
                .build();
    }

    /** 创建 Agent 状态变更事件（无输出） */
    public static BlackboardProgressEvent agentStarted(String agentName, String message) {
        return new Builder()
                .type("AGENT_OUTPUT")
                .agentName(agentName)
                .agentStatus("running")
                .message(message)
                .build();
    }

    /** 创建 Agent 状态变更事件（含输入物料） */
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

    /** 创建 Agent 完成事件（含输出） */
    public static BlackboardProgressEvent agentCompleted(String agentName, String output) {
        return new Builder()
                .type("AGENT_OUTPUT")
                .agentName(agentName)
                .agentStatus("done")
                .output(output)
                .build();
    }

    /**
     * 创建 Agent 失败事件（如校验闸门未通过）
     *
     * <p>与普通 {@link #agentCompleted(String, String)} 的区别：前端会将对应 flow-node
     * 标为「失败」状态并停止推进下游节点，用于流水线被硬阻断的场景。
     */
    public static BlackboardProgressEvent agentFailed(String agentName, String output) {
        return new Builder()
                .type("AGENT_OUTPUT")
                .agentName(agentName)
                .agentStatus("failed")
                .output(output)
                .build();
    }

    /**
     * 创建 Token 增量事件（真流式输出）
     *
     * <p>每当底层 LLM 流式返回一小段内容时推送，前端按 {@code kind} 分别追加到 「思考链」或「正式输出」区域。与快照式 {@link
     * #agentCompleted(String, String)} 并存：token 流负责实时逐字，完成时的 AGENT_OUTPUT(done) 负责最终收敛。
     *
     * @param agentName Agent 名称
     * @param kind 增量类型：output（正式输出）/ thinking（思考链）
     * @param delta 增量文本片段
     */
    public static BlackboardProgressEvent tokenDelta(String agentName, String kind, String delta) {
        return new Builder()
                .type("AGENT_TOKEN")
                .agentName(agentName)
                .kind(kind)
                .delta(delta)
                .build();
    }

    /** 创建整体完成事件 */
    public static BlackboardProgressEvent completed(
            BlackboardState blackboard, int retrievedChunks) {
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

    /** 创建出卷完成事件 */
    public static BlackboardProgressEvent examCompleted(
            BlackboardState blackboard, int retrievedChunks) {
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

    /** 创建题型分布方案完成事件（两步式流程第一步产出） */
    public static BlackboardProgressEvent distributionCompleted(String planJson) {
        return new Builder()
                .type("COMPLETED")
                .phase(BlackboardPhase.COMPLETED)
                .distributionPlan(planJson)
                .build();
    }

    /** 创建错误事件 */
    public static BlackboardProgressEvent error(String errorMessage) {
        return new Builder()
                .type("ERROR")
                .phase(BlackboardPhase.FAILED)
                .errorMessage(errorMessage)
                .build();
    }

    // ==================== Getters ====================

    public String getType() {
        return type;
    }

    public BlackboardPhase getPhase() {
        return phase;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getAgentStatus() {
        return agentStatus;
    }

    public String getKind() {
        return kind;
    }

    public String getDelta() {
        return delta;
    }

    public String getOutput() {
        return output;
    }

    public String getMessage() {
        return message;
    }

    public String getFinalArticle() {
        return finalArticle;
    }

    public String getKeyFindings() {
        return keyFindings;
    }

    public String getDraftArticle() {
        return draftArticle;
    }

    public String getReviewFeedback() {
        return reviewFeedback;
    }

    public int getQualityScore() {
        return qualityScore;
    }

    public int getRetrievedChunks() {
        return retrievedChunks;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getMaterials() {
        return materials;
    }

    public String getExamPaper() {
        return examPaper;
    }

    public String getAnswerKey() {
        return answerKey;
    }

    public String getExamReviewFeedback() {
        return examReviewFeedback;
    }

    public String getDifficultyAssessment() {
        return difficultyAssessment;
    }

    public String getDeduplicationReport() {
        return deduplicationReport;
    }

    public String getDistributionPlan() {
        return distributionPlan;
    }

    public Integer getRound() {
        return round;
    }

    public Integer getMaxRound() {
        return maxRound;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    /** Builder 模式构建事件 */
    public static class Builder {
        private String type;
        private BlackboardPhase phase;
        private String agentName;
        private String agentStatus;
        private String kind;
        private String delta;
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
        private String distributionPlan;
        private Integer round;
        private Integer maxRound;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder phase(BlackboardPhase phase) {
            this.phase = phase;
            return this;
        }

        public Builder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public Builder agentStatus(String agentStatus) {
            this.agentStatus = agentStatus;
            return this;
        }

        public Builder kind(String kind) {
            this.kind = kind;
            return this;
        }

        public Builder delta(String delta) {
            this.delta = delta;
            return this;
        }

        public Builder output(String output) {
            this.output = output;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder finalArticle(String finalArticle) {
            this.finalArticle = finalArticle;
            return this;
        }

        public Builder keyFindings(String keyFindings) {
            this.keyFindings = keyFindings;
            return this;
        }

        public Builder draftArticle(String draftArticle) {
            this.draftArticle = draftArticle;
            return this;
        }

        public Builder reviewFeedback(String reviewFeedback) {
            this.reviewFeedback = reviewFeedback;
            return this;
        }

        public Builder qualityScore(int qualityScore) {
            this.qualityScore = qualityScore;
            return this;
        }

        public Builder retrievedChunks(int retrievedChunks) {
            this.retrievedChunks = retrievedChunks;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder materials(String materials) {
            this.materials = materials;
            return this;
        }

        public Builder examPaper(String examPaper) {
            this.examPaper = examPaper;
            return this;
        }

        public Builder answerKey(String answerKey) {
            this.answerKey = answerKey;
            return this;
        }

        public Builder examReviewFeedback(String examReviewFeedback) {
            this.examReviewFeedback = examReviewFeedback;
            return this;
        }

        public Builder difficultyAssessment(String difficultyAssessment) {
            this.difficultyAssessment = difficultyAssessment;
            return this;
        }

        public Builder deduplicationReport(String deduplicationReport) {
            this.deduplicationReport = deduplicationReport;
            return this;
        }

        public Builder distributionPlan(String distributionPlan) {
            this.distributionPlan = distributionPlan;
            return this;
        }

        public Builder round(Integer round) {
            this.round = round;
            return this;
        }

        public Builder maxRound(Integer maxRound) {
            this.maxRound = maxRound;
            return this;
        }

        public BlackboardProgressEvent build() {
            return new BlackboardProgressEvent(this);
        }
    }
}
