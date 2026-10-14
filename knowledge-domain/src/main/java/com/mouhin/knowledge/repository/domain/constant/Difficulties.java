package com.mouhin.knowledge.repository.domain.constant;

/**
 * 难度标识常量。
 *
 * <p>出卷、分值分配、难度校准等链路统一在此维护难度 code，禁止硬编码字符串字面量（AGENTS.md 红线 #2）。 code 与数据库存储、LLM 结构化输出保持一致。
 */
public final class Difficulties {

    private Difficulties() {}

    /** 简单 */
    public static final String EASY = "EASY";

    /** 中等 */
    public static final String MEDIUM = "MEDIUM";

    /** 困难 */
    public static final String HARD = "HARD";

    /** 缺省难度（入参为 {@code null} 时回退）。 */
    public static final String DEFAULT = MEDIUM;
}
