package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 基于已上传文档解析预览查询（client 层，不入库）。
 *
 * <p>封装预览用例入参：目标文档 Key 与分块三件套（{@code chunkSize / overlap / strategy}）， 收敛为查询对象，避免 4 个位置参数（AGENTS.md
 * §十）。
 *
 * @author mouhinU
 * @date 2026-09-21
 */
@Getter
@Setter
public class PreviewDocumentQuery {

    /** 目标文档 Key */
    private String documentKey;

    /** 分块大小（字符数），默认 500 */
    private Integer chunkSize = 500;

    /** 重叠字符数，默认 50 */
    private Integer overlap = 50;

    /** 分块策略，默认 FIXED_SIZE */
    private String strategy = "FIXED_SIZE";
}
