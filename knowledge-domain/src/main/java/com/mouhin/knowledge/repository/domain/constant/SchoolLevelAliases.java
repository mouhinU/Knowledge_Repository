package com.mouhin.knowledge.repository.domain.constant;

/**
 * 学段中文别名常量。
 *
 * <p>{@code ScoreRuleEngine#parseLevelCode} 容错分支既接受英文枚举码（{@code "PRIMARY"} 等）， 也接受中文别名（{@code "小学"}
 * 等）。中文别名既出现在 {@code case} 标签（parser 输入），也 出现在 {@code SchoolLevel} 枚举展示映射（{@code
 * toChinese}）返回值，两处共享同一字面量。 依 AGENTS.md 红线 #2 集中维护。
 */
public final class SchoolLevelAliases {

    private SchoolLevelAliases() {}

    /** 小学。 */
    public static final String PRIMARY_ZH = "小学";

    /** 初中（规范简写）。 */
    public static final String JUNIOR_ZH = "初中";

    /** 高中（规范简写）。 */
    public static final String SENIOR_ZH = "高中";
}
