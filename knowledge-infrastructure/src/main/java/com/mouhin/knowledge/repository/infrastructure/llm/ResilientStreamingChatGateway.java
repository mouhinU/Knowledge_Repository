package com.mouhin.knowledge.repository.infrastructure.llm;

import com.mouhin.knowledge.repository.domain.service.StreamingChatGateway;

/**
 * {@link StreamingChatGateway} 韧性装饰器（Phase R2）。
 *
 * <p>包裹真实流式对话网关（黑板 Agent / 出卷逐 token 推送），把调用纳入 {@link LlmResilience} 的 {@code chat-stream} 角色护栏。该角色
 * {@code maxAttempts=1}，{@link LlmResilience} 因此只挂熔断快速失败与计时、<b>不挂重试</b>： 流式一旦向 UI
 * 吐字，重试会重复推送增量造成内容错乱，故仅在网络连接建立前失败时才由熔断器保护，绝不重放整段流。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
public class ResilientStreamingChatGateway implements StreamingChatGateway {

    private final StreamingChatGateway delegate;
    private final LlmResilience resilience;

    public ResilientStreamingChatGateway(StreamingChatGateway delegate, LlmResilience resilience) {
        this.delegate = delegate;
        this.resilience = resilience;
    }

    @Override
    public String streamCompletion(
            String systemPrompt, String userPrompt, StreamDeltaHandler handler) {
        return resilience.run(
                "chat-stream", () -> delegate.streamCompletion(systemPrompt, userPrompt, handler));
    }
}
