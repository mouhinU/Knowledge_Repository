package com.mouhin.knowledge.repository.infrastructure.llm;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.gateway.TenantUsageRecorder;
import com.mouhin.knowledge.repository.domain.model.valueobject.UsageMetric;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * EmbeddingModel 用量采集装饰器（P6-T1）。
 *
 * <p>包装实际 {@link EmbeddingModel} bean，在每次调用后从 {@code Response.tokenUsage()} 提取 token 消耗并记录。采集失败仅记
 * WARN 不阻塞业务。
 */
@Component
@Primary
@ConditionalOnProperty(name = "knowledge.saas.quota.enabled", havingValue = "true")
@Slf4j
public class UsageCapturingEmbeddingModelDecorator implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final TenantUsageRecorder usageRecorder;

    public UsageCapturingEmbeddingModelDecorator(
            @Qualifier("delegateEmbeddingModel") EmbeddingModel delegate,
            TenantUsageRecorder usageRecorder) {
        this.delegate = delegate;
        this.usageRecorder = usageRecorder;
    }

    @Override
    public Response<List<Embedding>> embedAll(
            List<dev.langchain4j.data.segment.TextSegment> textSegments) {
        Response<List<Embedding>> response = delegate.embedAll(textSegments);
        captureUsage(response);
        return response;
    }

    private void captureUsage(Response<List<Embedding>> response) {
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
                usageRecorder.record(tenantKey, UsageMetric.EMBEDDING_TOKENS, total);
            }
        } catch (Exception e) {
            log.warn("[usage] embedding token capture failed: {}", e.getMessage());
        }
    }
}
