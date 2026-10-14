package com.mouhin.knowledge.repository.client.dto;

import com.mouhin.knowledge.repository.client.constant.ChunkingDefaults;
import lombok.Getter;
import lombok.Setter;

/**
 * 分块配置请求参数
 *
 * <p>用于解析预览和确认入库接口，统一封装分块相关配置。
 *
 * @author mouhinU
 * @date 2026-09-11
 */
@Getter
@Setter
public class ChunkingRequest {

    /** 分块大小（字符数），默认 500 */
    private Integer chunkSize = ChunkingDefaults.DEFAULT_CHUNK_SIZE;

    /** 重叠字符数，默认 50 */
    private Integer overlap = ChunkingDefaults.DEFAULT_OVERLAP;

    /** 分块策略，默认 FIXED_SIZE */
    private String strategy = ChunkingDefaults.STRATEGY_FIXED_SIZE;
}
