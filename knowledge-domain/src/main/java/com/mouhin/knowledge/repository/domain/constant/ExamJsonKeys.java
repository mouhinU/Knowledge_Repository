package com.mouhin.knowledge.repository.domain.constant;

/**
 * 试卷/题目 JSON 协议字段名常量。
 *
 * <p>试卷解析（{@code ExamPaperParser}）与内容渲染（{@code ExamContentRenderAgent}）以 JSON-like {@code
 * Map<String, Object>} 交换题目对象，{@code type} / {@code content} / {@code options} 三键在生成侧与消费侧共享。依
 * AGENTS.md 红线 #2 集中维护。
 */
public final class ExamJsonKeys {

    private ExamJsonKeys() {}

    /** 题型字段。 */
    public static final String KEY_TYPE = "type";

    /** 题干/正文字段。 */
    public static final String KEY_CONTENT = "content";

    /** 选项数组字段。 */
    public static final String KEY_OPTIONS = "options";

    /** 参考答案字段。 */
    public static final String KEY_ANSWER = "answer";

    /** 分值字段。 */
    public static final String KEY_SCORE = "score";

    /** 解析/说明字段。 */
    public static final String KEY_EXPLANATION = "explanation";

    /** 知识点标签字段。 */
    public static final String KEY_KNOWLEDGE_POINT = "knowledge_point";

    /** 难度字段。 */
    public static final String KEY_DIFFICULTY = "difficulty";
}
