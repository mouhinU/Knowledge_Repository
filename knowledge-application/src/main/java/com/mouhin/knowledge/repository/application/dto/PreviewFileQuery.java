package com.mouhin.knowledge.repository.application.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * 直接解析上传文件预览查询（app 内部参数 DTO）。
 *
 * <p>封装「上传文件 + 分块三件套」入参，收敛 {@code PreviewFileQryExe.execute} 的 4 个位置参数（AGENTS.md §十）。 含 {@link
 * MultipartFile}（传输耦合），故置于 app 内部而非 client 契约：该用例由适配层直接调用执行器，不进对外接口。当前无 适配层调用点，作为完整用例保留。
 *
 * @author mouhinU
 * @date 2026-09-21
 */
@Getter
@Setter
public class PreviewFileQuery {

    /** 待解析的上传文件 */
    private MultipartFile file;

    /** 分块大小（字符数），默认 500 */
    private Integer chunkSize = 500;

    /** 重叠字符数，默认 50 */
    private Integer overlap = 50;

    /** 分块策略，默认 FIXED_SIZE */
    private String strategy = "FIXED_SIZE";
}
