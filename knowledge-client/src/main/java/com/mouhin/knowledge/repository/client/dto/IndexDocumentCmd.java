package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 确认入库命令（client 层）。
 *
 * <p>封装同步入库用例入参：目标文档 Key 与分块配置（{@code chunkSize / overlap / strategy}）。 三件套恒常一起传递，统一收敛为命令对象，避免 4
 * 个位置参数（AGENTS.md §十）。
 *
 * @author mouhinU
 * @date 2026-09-21
 */
@Getter
@Setter
public class IndexDocumentCmd {

    /** 目标文档 Key */
    private String documentKey;

    /** 分块大小（字符数），默认 500 */
    private Integer chunkSize = 500;

    /** 重叠字符数，默认 50 */
    private Integer overlap = 50;

    /** 分块策略，默认 FIXED_SIZE */
    private String strategy = "FIXED_SIZE";
}
