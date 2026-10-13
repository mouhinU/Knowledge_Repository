package com.mouhin.knowledge.repository.domain.constant;

/**
 * Agent 名称常量。
 *
 * <p>所有 Agent 在进度上报（{@code BlackboardProgressEvent}）、审计日志 （{@code BlackboardAuditLog}）、指标采集（{@code
 * BlackboardMetrics}） 以及线程池命名中使用的标识符统一在此维护， 禁止在业务代码中硬编码字符串字面量。
 *
 * <p>常量分为以下几类：
 *
 * <ul>
 *   <li><b>AGENT_*</b> — kebab-case，用于进度事件与审计/指标
 *   <li><b>PIPELINE_*</b> — 流水线级别聚合名称
 *   <li><b>POOL_*</b> — 线程池名称前缀
 *   <li><b>DISPLAY_*</b> — PascalCase，Agent {@code getName()} 返回值， 经 {@code
 *       BlackboardAgentDecorator} 流入审计与指标
 *   <li><b>ACTOR_*</b> — 系统级行为者标识
 * </ul>
 */
public final class AgentNames {

    private AgentNames() {}

    // ── 出卷链路 Agent ──────────────────────────────────────

    /** 研究检索 Agent */
    public static final String AGENT_EXAM_RESEARCHER = "exam-researcher";

    /** 审题 Agent */
    public static final String AGENT_EXAM_REVIEWER = "exam-reviewer";

    /** 评分 Agent */
    public static final String AGENT_EXAM_SCORING = "exam-scoring";

    /** 标准答案生成 Agent */
    public static final String AGENT_ANSWER_GENERATOR = "answer-generator";

    /** 知识缺口分析 Agent */
    public static final String AGENT_KNOWLEDGE_GAP_ANALYZER = "knowledge-gap-analyzer";

    /** 难度校准 Agent */
    public static final String AGENT_EXAM_CALIBRATOR = "exam-calibrator";

    /** 知识点去重 Agent */
    public static final String AGENT_EXAM_KP_DEDUP = "exam-kp-dedup";

    /** 错题分析 Agent */
    public static final String AGENT_WRONG_ANSWER_ANALYZER = "wrong-answer-analyzer";

    /** 出卷写作 Agent */
    public static final String AGENT_EXAM_WRITER = "exam-writer";

    /** 试卷去重 Agent */
    public static final String AGENT_EXAM_DEDUPLICATOR = "exam-deduplicator";

    /** 出卷计划校验 Agent */
    public static final String AGENT_EXAM_PLAN_VALIDATOR = "exam-plan-validator";

    /** 分布调整 Agent（ExamDistributionAgent 内部调整阶段） */
    public static final String AGENT_DIST_ADJUST = "dist-adjust";

    // ── 写作链路 Agent ──────────────────────────────────────

    /** 通用研究 Agent */
    public static final String AGENT_RESEARCHER = "researcher";

    /** 评审 Agent */
    public static final String AGENT_REVIEWER = "reviewer";

    /** 写作 Agent */
    public static final String AGENT_WRITER = "writer";

    /** 难度校准 Agent（写作链路，Bloom 校准） */
    public static final String AGENT_BLOOM_CALIBRATOR = "bloom-calibrator";

    // ── 流水线级聚合名称 ────────────────────────────────────

    /** 出卷流水线（审计/指标聚合） */
    public static final String PIPELINE_EXAM = "pipeline";

    /** 研究+评分复合名称（审计聚合） */
    public static final String PIPELINE_RESEARCHER_SCORING = "researcher+scoring";

    /** 文章生成流水线（审计/指标聚合） */
    public static final String PIPELINE_ARTICLE = "article-pipeline";

    // ── 线程池名称前缀 ──────────────────────────────────────

    /** 出卷线程池前缀 */
    public static final String POOL_EXAM_GEN = "exam-gen";

    /** 评分线程池前缀 */
    public static final String POOL_EXAM_GRADING = "exam-grading";

    /** 文章生成线程池前缀 */
    public static final String POOL_ARTICLE_GEN = "article-gen";

    // ── PascalCase 显示名（Agent.getName() 返回值） ─────────

    /** ExamResearcher Agent 显示名 */
    public static final String DISPLAY_EXAM_RESEARCHER = "ExamResearcher";

    /** Reviewer Agent 显示名 */
    public static final String DISPLAY_REVIEWER = "Reviewer";

    /** ExamScoring Agent 显示名 */
    public static final String DISPLAY_EXAM_SCORING = "ExamScoring";

    /** AnswerKeyGenerator Agent 显示名 */
    public static final String DISPLAY_ANSWER_KEY_GENERATOR = "AnswerKeyGenerator";

    /** KnowledgeGapAnalyzer Agent 显示名 */
    public static final String DISPLAY_KNOWLEDGE_GAP_ANALYZER = "KnowledgeGapAnalyzer";

    /** ExamCalibrator Agent 显示名 */
    public static final String DISPLAY_EXAM_CALIBRATOR = "ExamCalibrator";

    /** ExamKnowledgeDedup Agent 显示名 */
    public static final String DISPLAY_EXAM_KP_DEDUP = "ExamKnowledgeDedup";

    /** WrongAnswerAnalyzer Agent 显示名 */
    public static final String DISPLAY_WRONG_ANSWER_ANALYZER = "WrongAnswerAnalyzer";

    /** Researcher Agent 显示名 */
    public static final String DISPLAY_RESEARCHER = "Researcher";

    /** ExamReviewer Agent 显示名 */
    public static final String DISPLAY_EXAM_REVIEWER = "ExamReviewer";

    /** ExamDeduplicator Agent 显示名 */
    public static final String DISPLAY_EXAM_DEDUPLICATOR = "ExamDeduplicator";

    /** Writer Agent 显示名 */
    public static final String DISPLAY_WRITER = "Writer";

    /** DifficultyCalibrator Agent 显示名 */
    public static final String DISPLAY_DIFFICULTY_CALIBRATOR = "DifficultyCalibrator";

    /** ExamWriter Agent 显示名 */
    public static final String DISPLAY_EXAM_WRITER = "ExamWriter";

    // ── 系统级行为者 ────────────────────────────────────────

    /** 系统自动操作（租户审计日志） */
    public static final String ACTOR_SYSTEM = "system";
}
