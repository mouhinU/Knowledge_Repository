package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 分块配置值对象
 *
 * @author mouhinU
 * @date 2026-09-02
 */
public final class ChunkingConfig {

    private static final int DEFAULT_MAX_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP_SIZE = 50;
    private static final ChunkingStrategyEnum DEFAULT_STRATEGY = ChunkingStrategyEnum.FIXED_SIZE;

    /** 每个分块最大 token 数 */
    private final int maxChunkSize;

    /** 相邻分块重叠 token 数 */
    private final int overlapSize;

    /** 切分策略 */
    private final ChunkingStrategyEnum strategy;

    /** 是否按段落边界分块（仅 FIXED_SIZE 策略兼容） */
    private final boolean respectParagraphBoundary;

    /** 是否按页面边界分块 */
    private final boolean respectPageBoundary;

    public ChunkingConfig(
            int maxChunkSize,
            int overlapSize,
            boolean respectParagraphBoundary,
            boolean respectPageBoundary) {
        this(
                maxChunkSize,
                overlapSize,
                DEFAULT_STRATEGY,
                respectParagraphBoundary,
                respectPageBoundary);
    }

    public ChunkingConfig(
            int maxChunkSize,
            int overlapSize,
            ChunkingStrategyEnum strategy,
            boolean respectParagraphBoundary,
            boolean respectPageBoundary) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize must be positive");
        }
        if (overlapSize < 0 || overlapSize >= maxChunkSize) {
            throw new IllegalArgumentException("overlapSize must be in [0, maxChunkSize)");
        }
        this.maxChunkSize = maxChunkSize;
        this.overlapSize = overlapSize;
        this.strategy = strategy != null ? strategy : DEFAULT_STRATEGY;
        this.respectParagraphBoundary = respectParagraphBoundary;
        this.respectPageBoundary = respectPageBoundary;
    }

    public static ChunkingConfig defaultConfig() {
        return new ChunkingConfig(
                DEFAULT_MAX_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE, DEFAULT_STRATEGY, true, true);
    }

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    public int getOverlapSize() {
        return overlapSize;
    }

    public ChunkingStrategyEnum getStrategy() {
        return strategy;
    }

    public boolean isRespectParagraphBoundary() {
        return respectParagraphBoundary;
    }

    public boolean isRespectPageBoundary() {
        return respectPageBoundary;
    }
}
