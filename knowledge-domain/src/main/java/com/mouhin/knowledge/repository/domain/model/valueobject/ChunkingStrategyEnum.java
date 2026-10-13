package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 文档切分策略枚举
 *
 * @author mouhinU
 * @date 2026-09-10
 */
public enum ChunkingStrategyEnum {

    /** 固定 Token 大小切分（按段落边界 + 重叠） */
    FIXED_SIZE,

    /** 递归字符切分（按分隔符层级：段落 → 句子 → 词 → 字符） */
    RECURSIVE,

    /** 按句子切分，合并至 Token 上限 */
    SENTENCE,

    /** 每页作为一个分块（不跨页、不切分） */
    PAGE,

    /** 按段落切分，小段落合并至 Token 上限 */
    PARAGRAPH
}
