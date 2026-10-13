package com.mouhin.knowledge.repository.client.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库语义检索响应（adapter 响应体）
 *
 * <p>结构与既有 POST /api/knowledge/search 的 JSON 一致：query / totalResults / results[]， 其中 results 每项含
 * text / documentKey / documentName / pageNumber / chunkIndex / score / category， null 归一为 0
 * 或空串，score 保留 4 位小数。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Getter
@Setter
public class SearchResponseVO {

    private String query;
    private int totalResults;
    private List<ItemVO> results;

    /** 单条检索命中 */
    @Getter
    @Setter
    public static class ItemVO {
        private String text;
        private String documentKey;
        private String documentName;
        private int pageNumber;
        private int chunkIndex;
        private double score;
        private String category;
    }
}
