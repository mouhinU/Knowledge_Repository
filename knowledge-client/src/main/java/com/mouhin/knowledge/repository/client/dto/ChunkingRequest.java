package com.mouhin.knowledge.repository.client.dto;

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
    private Integer chunkSize = 500;

    /** 重叠字符数，默认 50 */
    private Integer overlap = 50;

    /** 分块策略，默认 FIXED_SIZE */
    private String strategy = "FIXED_SIZE";
}
