package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 分块配置值对象
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
public final class ChunkingConfig {

    private static final int DEFAULT_MAX_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP_SIZE = 50;

    /** 每个分块最大 token 数 */
    private final int maxChunkSize;

    /** 相邻分块重叠 token 数 */
    private final int overlapSize;

    /** 是否按段落边界分块 */
    private final boolean respectParagraphBoundary;

    /** 是否按页面边界分块 */
    private final boolean respectPageBoundary;

    public ChunkingConfig(int maxChunkSize, int overlapSize,
                          boolean respectParagraphBoundary, boolean respectPageBoundary) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize must be positive");
        }
        if (overlapSize < 0 || overlapSize >= maxChunkSize) {
            throw new IllegalArgumentException("overlapSize must be in [0, maxChunkSize)");
        }
        this.maxChunkSize = maxChunkSize;
        this.overlapSize = overlapSize;
        this.respectParagraphBoundary = respectParagraphBoundary;
        this.respectPageBoundary = respectPageBoundary;
    }

    public static ChunkingConfig defaultConfig() {
        return new ChunkingConfig(DEFAULT_MAX_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE, true, true);
    }

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    public int getOverlapSize() {
        return overlapSize;
    }

    public boolean isRespectParagraphBoundary() {
        return respectParagraphBoundary;
    }

    public boolean isRespectPageBoundary() {
        return respectPageBoundary;
    }
}
