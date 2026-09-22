package com.mouhin.knowledge.repository.infrastructure.llm;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.List;

/**
 * {@link EmbeddingModel} 韧性装饰器（Phase R2）。
 *
 * <p>包裹真实 {@link EmbeddingModel}，把向量化调用纳入 {@link LlmResilience} 的 {@code embedding} 角色熔断 + 重试 + 计时。
 * 向量调用短平快，独立于 chat 长对话，抖动时快速失败以免拖垮向量化批次。{@code embed(String)} / {@code embed(TextSegment)}
 * 便捷重载在接口默认实现里最终收敛到 {@link #embedAll(List)}，故只需覆写该方法；{@code dimension()} 直接转发，
 * 避免为探测维度额外触发一次带重试的网络调用。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
public class ResilientEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final LlmResilience resilience;

    public ResilientEmbeddingModel(EmbeddingModel delegate, LlmResilience resilience) {
        this.delegate = delegate;
        this.resilience = resilience;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        return resilience.run("embedding", () -> delegate.embedAll(textSegments));
    }

    @Override
    public int dimension() {
        return delegate.dimension();
    }
}
