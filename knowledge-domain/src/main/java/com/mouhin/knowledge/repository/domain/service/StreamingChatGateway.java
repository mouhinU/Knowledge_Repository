package com.mouhin.knowledge.repository.domain.service;

/**
 * 流式对话网关（领域层接口，出入参仅用 JDK 类型，保持领域纯净）。
 * <p>
 * 屏蔽底层 LLM 提供商差异，向调用方以「增量回调」的方式流式吐字，
 * 方法在流结束后返回完整文本。用于黑板 Agent 与主观题评分的实时输出场景。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public interface StreamingChatGateway {

    /** 增量类型：正常回答内容 */
    String KIND_OUTPUT = "output";

    /** 增量类型：思考链 / 推理内容 */
    String KIND_THINKING = "thinking";

    /**
     * 以流式方式发起一次单轮对话（system + user），阻塞直到模型输出结束。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param handler      增量回调，可为 null（不需要流式时）；由底层线程调用
     * @return 模型返回的完整回答文本（不含思考链）
     */
    String streamCompletion(String systemPrompt, String userPrompt, StreamDeltaHandler handler);

    /**
     * 流式增量回调。
     */
    @FunctionalInterface
    interface StreamDeltaHandler {

        /**
         * 收到一段增量。
         *
         * @param kind  {@link #KIND_OUTPUT} 或 {@link #KIND_THINKING}
         * @param delta 本次增量文本片段
         */
        void onDelta(String kind, String delta);
    }
}
