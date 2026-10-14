package com.mouhin.knowledge.repository.domain.constant;

/**
 * 文本截断省略号标记常量。
 *
 * <p>日志/摘要/上下文截断后追加的省略号标记（ASCII 三小点 {@code "..."} 与 Unicode 单字符 {@code "…"}） 属于展示层约定，依 AGENTS.md 红线
 * #2 集中维护，避免各 Agent/Service 各自写字面量。
 */
public final class TruncationMarkers {

    private TruncationMarkers() {}

    /** ASCII 三小点省略号（英文/日志摘要场景）。 */
    public static final String ELLIPSIS = "...";

    /** Unicode 单字符省略号（中文 UI 文案场景）。 */
    public static final String WIDE_ELLIPSIS = "…";
}
