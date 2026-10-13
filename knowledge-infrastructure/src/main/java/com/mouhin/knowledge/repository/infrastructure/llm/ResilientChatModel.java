package com.mouhin.knowledge.repository.infrastructure.llm;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Set;

/**
 * {@link ChatModel} 韧性装饰器（Phase R2）。
 *
 * <p>包裹真实 {@link ChatModel}，把非流式对话调用纳入 {@link LlmResilience} 的 {@code chat} 角色熔断 + 重试 + 计时。 应用层统一以
 * {@code chat(ChatRequest)} 为入口（{@code chat(String)} 等便捷重载在接口默认实现里最终都会收敛到此）， 故只需覆写该方法并把 {@code
 * provider / defaultRequestParameters / listeners / supportedCapabilities} 原样转发给被包裹对象， 即保持与裸 {@link
 * ChatModel} 完全一致的监听器与参数解析行为，仅在外层叠加韧性护栏。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
public class ResilientChatModel implements ChatModel {

    private final ChatModel delegate;
    private final LlmResilience resilience;

    public ResilientChatModel(ChatModel delegate, LlmResilience resilience) {
        this.delegate = delegate;
        this.resilience = resilience;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        return resilience.run("chat", () -> delegate.chat(chatRequest));
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }
}
