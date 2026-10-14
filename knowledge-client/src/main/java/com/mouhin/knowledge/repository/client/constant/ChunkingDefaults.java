package com.mouhin.knowledge.repository.client.constant;

/**
 * 分块预览/索引请求的缺省值常量（client 层）。
 *
 * <p>client 模块不依赖 domain，无法引用 {@code ChunkingStrategyEnum}，故在此维护与领域枚举 {@code
 * ChunkingStrategyEnum.FIXED_SIZE} 对齐的缺省策略名与分块参数，供 {@code ChunkingRequest} / {@code
 * PreviewDocumentQuery} / {@code IndexDocumentCmd} 复用，消除此前三份重复的字面量默认值（AGENTS.md 红线 #2）。
 *
 * <p>注意：此处常量值必须与 domain 侧 {@code ChunkingStrategyEnum.FIXED_SIZE.name()} 保持一致；若领域枚举重命名，需同步更新。
 */
public final class ChunkingDefaults {

    private ChunkingDefaults() {}

    /** 缺省分块策略：固定长度（对齐 {@code ChunkingStrategyEnum.FIXED_SIZE}）。 */
    public static final String STRATEGY_FIXED_SIZE = "FIXED_SIZE";

    /** 缺省分块大小（token）。 */
    public static final int DEFAULT_CHUNK_SIZE = 500;

    /** 缺省分块重叠（token）。 */
    public static final int DEFAULT_OVERLAP = 50;
}
