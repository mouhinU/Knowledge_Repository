package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 分块窗口（domain 层值对象）。
 *
 * <p>承载一次重叠计算所需的三个恒常同行的量：窗口字符起止 {@code [chunkStart, chunkEnd)} 与 期望重叠 token 数。收敛 {@code
 * findOverlapStart} 的位置参数，避免原始类型长参数列表（AGENTS.md §十）。
 *
 * @param chunkStart 当前块起始字符下标（含）
 * @param chunkEnd 当前块结束字符下标（不含）
 * @param overlapTokens 期望重叠的 token 数
 * @author mouhinU
 * @date 2026-09-21
 */
public record ChunkWindow(int chunkStart, int chunkEnd, int overlapTokens) {}
