package com.mouhin.knowledge.repository.domain.service;

/**
 * 考试评分进度回调接口
 *
 * <p>评分过程中上报每题的输入 / 原始输出 / 解析结果，供管理端 SSE 实时展示。 由应用层定义、Web 层实现（持有 SseEmitter 并推送事件）。
 *
 * @author mouhinU
 * @date 2026-09-16
 */
public interface ExamGradingProgressCallback {

    /**
     * 单题评分开始
     *
     * @param questionIndex 题号（从 1 开始）
     * @param questionType 题型
     * @param aiInput 该题评分输入（Prompt / 比对依据）
     */
    void onQuestionStart(int questionIndex, String questionType, String aiInput);

    /**
     * 主观题 AI 评分的逐 token 增量（真流式输出）。
     *
     * <p>默认空实现，便于不需要流式的实现方忽略。
     *
     * @param questionIndex 题号（从 1 开始）
     * @param kind 增量类型：{@code output}（正式输出）/ {@code thinking}（思考链）
     * @param delta 本次增量文本片段
     */
    default void onQuestionToken(int questionIndex, String kind, String delta) {
        // 默认忽略，由需要流式展示的实现方覆写
    }

    /**
     * 单题评分完成
     *
     * @param questionIndex 题号
     * @param aiRawOutput 模型原始返回文本（或客观题的比对结果说明）
     * @param aiScore 解析出的得分
     * @param maxScore 该题满分
     * @param aiFeedback 评分理由
     * @param elapsedMs 该题耗时（毫秒）
     */
    void onQuestionDone(
            int questionIndex,
            String aiRawOutput,
            int aiScore,
            int maxScore,
            String aiFeedback,
            long elapsedMs);

    /**
     * 单题评分失败
     *
     * @param questionIndex 题号
     * @param errorMessage 错误描述
     */
    void onQuestionError(int questionIndex, String errorMessage);

    /**
     * 全部评分完成
     *
     * @param totalQuestions 总题数
     * @param totalAiScore AI 评分合计
     */
    void onComplete(int totalQuestions, int totalAiScore);

    /**
     * 评分整体失败（如场次不存在、状态非法）
     *
     * @param errorMessage 错误描述
     */
    void onError(String errorMessage);
}
