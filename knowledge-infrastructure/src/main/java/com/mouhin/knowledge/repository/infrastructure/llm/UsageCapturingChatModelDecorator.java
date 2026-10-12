package com.mouhin.knowledge.repository.infrastructure.llm;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.gateway.TenantUsageRecorder;
import com.mouhin.knowledge.repository.domain.model.valueobject.UsageMetric;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * ChatModel 用量采集装饰器（P6-T1）。
 *
 * <p>包装实际 {@link ChatModel} bean，在每次非流式调用后从 {@code ChatResponse.tokenUsage()} 提取 token 消耗并通过 {@link
 * TenantUsageRecorder} 记录到 {@code tenant_usage} 表。 采集失败仅记 WARN 不阻塞业务。
 *
 * <p>流式调用（{@code StreamingChatGateway}）的 token 暂不计入（已知缺口，留 TODO）。
 */
@Component
@Primary
@ConditionalOnProperty(name = "knowledge.saas.quota.enabled", havingValue = "true")
@Slf4j
public class UsageCapturingChatModelDecorator implements ChatModel {

    private final ChatModel delegate;
    private final TenantUsageRecorder usageRecorder;

    public UsageCapturingChatModelDecorator(
            @Qualifier("delegateChatModel") ChatModel delegate, TenantUsageRecorder usageRecorder) {
        this.delegate = delegate;
        this.usageRecorder = usageRecorder;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        ChatResponse response = delegate.chat(chatRequest);
        captureUsage(response);
        return response;
    }

    private void captureUsage(ChatResponse response) {
        try {
            if (response == null) {
                return;
            }
            var tokenUsage = response.tokenUsage();
            if (tokenUsage == null) {
                return;
            }
            String tenantKey = TenantContext.getOrDefault();
            int input = tokenUsage.inputTokenCount() != null ? tokenUsage.inputTokenCount() : 0;
            int output = tokenUsage.outputTokenCount() != null ? tokenUsage.outputTokenCount() : 0;
            int total = input + output;
            if (total > 0) {
                usageRecorder.record(tenantKey, UsageMetric.CHAT_TOKENS, total);
            }
        } catch (Exception e) {
            log.warn("[usage] chat token capture failed: {}", e.getMessage());
        }
    }
}
