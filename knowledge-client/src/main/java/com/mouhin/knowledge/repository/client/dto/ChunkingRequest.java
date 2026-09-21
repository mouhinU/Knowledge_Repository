package com.mouhin.knowledge.repository.client.dto;

/**
 * 分块配置请求参数
 *
 * <p>用于解析预览和确认入库接口，统一封装分块相关配置。
 *
 * @author Knowledge-Repository
 * @date 2026-09-11
 */
public class ChunkingRequest {

    /** 分块大小（字符数），默认 500 */
    private Integer chunkSize = 500;

    /** 重叠字符数，默认 50 */
    private Integer overlap = 50;

    /** 分块策略，默认 FIXED_SIZE */
    private String strategy = "FIXED_SIZE";

    public Integer getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(Integer chunkSize) {
        this.chunkSize = chunkSize;
    }

    public Integer getOverlap() {
        return overlap;
    }

    public void setOverlap(Integer overlap) {
        this.overlap = overlap;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }
}
