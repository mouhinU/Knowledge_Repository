package com.mouhin.knowledge.repository.application.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统配置默认值注册表（Tier 3）
 *
 * <p>当数据库无记录时，由此提供出厂默认值。同时记录每个配置键的元数据（值类型、说明、分组）， 供管理页面在数据库尚未写入时也能展示完整的配置列表。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
public final class SystemConfigDefaults {

    private SystemConfigDefaults() {}

    /** 默认值注册表：configKey → defaultValue */
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    /** 值类型注册表：configKey → valueType */
    private static final Map<String, String> VALUE_TYPES = new LinkedHashMap<>();

    /** 说明注册表：configKey → description */
    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    /** 分组注册表：configKey → category */
    private static final Map<String, String> CATEGORIES = new LinkedHashMap<>();

    static {
        // ── feature-toggle：考试特性开关 ──
        register(
                "exam.plan.auto-balance.enabled",
                "true",
                "BOOLEAN",
                "方案生成后基于合理性评估建议自动修正方案",
                "feature-toggle");
        register(
                "exam.plan.auto-balance.max-rounds",
                "3",
                "INTEGER",
                "方案自动修正最大轮次（1-5）",
                "feature-toggle");
        register(
                "exam.quality-score-threshold",
                "80",
                "INTEGER",
                "出卷质量分阈值（低于此值触发改进重试）",
                "feature-toggle");
        register("exam.review-required", "true", "BOOLEAN", "试卷是否需人工校对后方可发布", "feature-toggle");
        register(
                "exam.knowledge-dedup.enabled",
                "true",
                "BOOLEAN",
                "试卷编写后自动进行考点级去重（去除重复考点试题并替补新题）",
                "feature-toggle");
        register(
                "feature.vision.enabled",
                "false",
                "BOOLEAN",
                "PDF 视觉解析（扫描件 OCR + 混合提取）总开关",
                "feature-toggle");
        register(
                "feature.reranker.enabled",
                "false",
                "BOOLEAN",
                "检索结果重排序（BGE-Reranker 二次精排）",
                "feature-toggle");

        // ── exam：考试运行参数 ──
        register("exam.grading-delay-minutes", "30", "INTEGER", "自动评分延迟分钟数（等待所有考生交卷）", "exam");
        register("exam.grading-timeout-minutes", "15", "INTEGER", "评分超时分钟数（超时后强制结束评分）", "exam");

        // ── llm：模型运行参数（运行时覆盖 @ConfigurationProperties） ──
        register("llm.chat.temperature", "0.7", "DECIMAL", "对话角色温度（0.0-2.0，越高越随机）", "llm");
        register("llm.chat.max-tokens", "4096", "INTEGER", "对话角色单次最大输出 Token 数", "llm");
        register(
                "llm.chat.streaming-max-tokens",
                "16384",
                "INTEGER",
                "对话流式角色最大输出 Token 数（须大于非流式以容纳思考链）",
                "llm");
        register("llm.embedding.model-name", "bge-m3", "STRING", "向量模型名称（切换后须重建集合）", "llm");

        // ── search：检索参数 ──
        register("search.max-results", "20", "INTEGER", "知识库语义检索最大返回条数", "search");
        register("search.min-score", "0.3", "DECIMAL", "知识库语义检索最低相似度阈值", "search");

        // ── quality-gate：黑板流水线质量门禁 ──
        register(
                "quality-gate.research.min-chunks",
                "1",
                "INTEGER",
                "检索阶段最低结果条数（低于即建议重试）",
                "quality-gate");
        register(
                "quality-gate.research.low-confidence-threshold",
                "3",
                "INTEGER",
                "检索阶段低置信度条数阈值（低于标记中等置信度）",
                "quality-gate");
        register(
                "quality-gate.writing.min-length",
                "50",
                "INTEGER",
                "写作阶段最低输出字符数（过短视为无效输出）",
                "quality-gate");

        // ── extraction：文档解析参数 ──
        register(
                "extraction.max-pages-per-doc",
                "20",
                "INTEGER",
                "单文档最大解析页数（PDF 超出部分截断）",
                "extraction");
    }

    private static void register(
            String key,
            String defaultValue,
            String valueType,
            String description,
            String category) {
        DEFAULTS.put(key, defaultValue);
        VALUE_TYPES.put(key, valueType);
        DESCRIPTIONS.put(key, description);
        CATEGORIES.put(key, category);
    }

    /** 获取默认值字符串 */
    public static String getDefault(String configKey) {
        return DEFAULTS.getOrDefault(configKey, "");
    }

    /** 获取默认 int 值 */
    public static int getDefaultInt(String configKey) {
        String val = DEFAULTS.get(configKey);
        if (val == null) return 0;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 获取默认 boolean 值 */
    public static boolean getDefaultBoolean(String configKey) {
        return Boolean.parseBoolean(DEFAULTS.getOrDefault(configKey, "false"));
    }

    /** 获取值类型 */
    public static String getValueType(String configKey) {
        return VALUE_TYPES.getOrDefault(configKey, "STRING");
    }

    /** 获取说明 */
    public static String getDescription(String configKey) {
        return DESCRIPTIONS.getOrDefault(configKey, "");
    }

    /** 获取分组 */
    public static String getCategory(String configKey) {
        return CATEGORIES.getOrDefault(configKey, "general");
    }

    /** 获取全部已注册的默认配置键（供管理页面初始化展示） */
    public static Map<String, String> getAllDefaults() {
        return Map.copyOf(DEFAULTS);
    }
}
