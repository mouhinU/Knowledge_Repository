package com.mouhin.knowledge.repository.infrastructure.agent;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import com.mouhin.knowledge.repository.domain.service.StreamingChatGateway;
import org.springframework.stereotype.Component;

/**
 * 黑板 Agent 流式对话桥接。
 * <p>
 * 把 {@link StreamingChatGateway} 的逐 token 增量转成
 * {@link BlackboardProgressEvent} 的 AGENT_TOKEN 事件，经 {@link BlackboardProgressCallback}
 * 推给前端，使各 Agent 只需一行即可享受流式输出。返回模型完整回答文本，供 Agent 落库/后续处理。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class BlackboardAgentStreamer {

    private final StreamingChatGateway streamingChatGateway;

    public BlackboardAgentStreamer(StreamingChatGateway streamingChatGateway) {
        this.streamingChatGateway = streamingChatGateway;
    }

    /**
     * 以流式方式执行一次单轮对话，并把输出/思考增量转发到进度回调。
     *
     * @param agentName       Agent 名称（前端据此定位渲染面板）
     * @param systemPrompt    系统提示词
     * @param userPrompt      用户提示词
     * @param progressCallback 进度回调，可为 null
     * @return 模型完整回答文本
     */
    public String stream(String agentName, String systemPrompt, String userPrompt,
                         BlackboardProgressCallback progressCallback) {
        return streamingChatGateway.streamCompletion(systemPrompt, userPrompt, (kind, delta) -> {
            if (progressCallback != null && delta != null && !delta.isEmpty()) {
                progressCallback.onProgress(BlackboardProgressEvent.tokenDelta(agentName, kind, delta));
            }
        });
    }
}
