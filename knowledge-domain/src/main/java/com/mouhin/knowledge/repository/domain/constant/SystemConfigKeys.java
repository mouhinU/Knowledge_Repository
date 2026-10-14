package com.mouhin.knowledge.repository.domain.constant;

/**
 * 系统配置键常量。
 *
 * <p>所有通过 {@code SystemConfigService} 读取的配置键统一在此维护， 禁止在业务代码中硬编码字符串字面量。键名按"{模块}.{子模块}.{属性}" 三段式命名，与
 * {@code sys_config} 表中的 {@code config_key} 一一对应。
 */
public final class SystemConfigKeys {

    /** 语义检索最大返回条数 */
    public static final String SEARCH_MAX_RESULTS = "search.max-results";

    // ── 检索 ──────────────────────────────────────────────
    /** 语义检索最低相关度阈值 */
    public static final String SEARCH_MIN_SCORE = "search.min-score";

    /** 出卷质量分阈值（低于此分强制人工校对） */
    public static final String EXAM_QUALITY_SCORE_THRESHOLD = "exam.quality-score-threshold";

    // ── 考试 · 出卷 ────────────────────────────────────────
    /** 是否要求人工校对后才可发布 */
    public static final String EXAM_REVIEW_REQUIRED = "exam.review-required";

    /** 考试知识去重开关 */
    public static final String EXAM_KNOWLEDGE_DEDUP_ENABLED = "exam.knowledge-dedup.enabled";

    /** 题型分布自动平衡开关 */
    public static final String EXAM_PLAN_AUTO_BALANCE_ENABLED = "exam.plan.auto-balance.enabled";

    /** 题型分布自动平衡最大轮次 */
    public static final String EXAM_PLAN_AUTO_BALANCE_MAX_ROUNDS =
            "exam.plan.auto-balance.max-rounds";

    /** AI 评分延迟分钟数（交卷后多久触发评分） */
    public static final String EXAM_GRADING_DELAY_MINUTES = "exam.grading-delay-minutes";

    // ── 考试 · 评分 ────────────────────────────────────────
    /** AI 评分超时分钟数（Scheduler 恢复用） */
    public static final String EXAM_GRADING_TIMEOUT_MINUTES = "exam.grading-timeout-minutes";

    /** Research 阶段最小检索 chunk 数 */
    public static final String QUALITY_GATE_RESEARCH_MIN_CHUNKS =
            "quality-gate.research.min-chunks";

    // ── 质量门禁 · Research ────────────────────────────────
    /** Research 阶段低置信度阈值 */
    public static final String QUALITY_GATE_RESEARCH_LOW_CONFIDENCE_THRESHOLD =
            "quality-gate.research.low-confidence-threshold";

    /** Writing 阶段最小输出长度 */
    public static final String QUALITY_GATE_WRITING_MIN_LENGTH = "quality-gate.writing.min-length";

    // ── 质量门禁 · Writing ─────────────────────────────────

    // ── 特性开关 ──────────────────────────────────────────
    /** PDF 视觉解析（扫描件 OCR + 混合提取）总开关 */
    public static final String FEATURE_VISION_ENABLED = "feature.vision.enabled";

    /** 检索结果重排序（BGE-Reranker 二次精排）开关 */
    public static final String FEATURE_RERANKER_ENABLED = "feature.reranker.enabled";

    // ── LLM 运行参数 ──────────────────────────────────────
    /** 对话角色温度（0.0-2.0，越高越随机） */
    public static final String LLM_CHAT_TEMPERATURE = "llm.chat.temperature";

    /** 对话角色单次最大输出 Token 数 */
    public static final String LLM_CHAT_MAX_TOKENS = "llm.chat.max-tokens";

    /** 对话流式角色最大输出 Token 数（须大于非流式以容纳思考链） */
    public static final String LLM_CHAT_STREAMING_MAX_TOKENS = "llm.chat.streaming-max-tokens";

    /** 向量模型名称（切换后须重建集合） */
    public static final String LLM_EMBEDDING_MODEL_NAME = "llm.embedding.model-name";

    // ── 文档解析 ──────────────────────────────────────────
    /** 单文档最大解析页数（PDF 超出部分截断） */
    public static final String EXTRACTION_MAX_PAGES_PER_DOC = "extraction.max-pages-per-doc";

    private SystemConfigKeys() {}
}
