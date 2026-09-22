package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 全局配图检索查询（client 层，看图题配图选图）。
 *
 * <p>封装配图检索入参：关键词、来源文档 Key 过滤、分页 {@code limit / offset}， 收敛为查询对象，避免 4 个位置参数（AGENTS.md §十）。{@code
 * keyword} 与 {@code documentKey} 皆空时按最新入库顺序返回全部。
 *
 * @author mouhinU
 * @date 2026-09-21
 */
@Getter
@Setter
public class SearchImagesQuery {

    /** 单页默认条数 */
    private static final int DEFAULT_LIMIT = 60;

    /** 关键词（匹配来源文档名 / 文档 Key），可空 */
    private String keyword;

    /** 限定到某篇文档的 Key，可空 */
    private String documentKey;

    /** 单页条数 */
    private int limit = DEFAULT_LIMIT;

    /** 偏移量 */
    private int offset;
}
