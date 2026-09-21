package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.CustomChunkInput;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.client.dto.PreviewResult;
import java.util.List;

/**
 * 文档摄入应用服务契约（client 层，仅承载传输无关的确认类用例）
 *
 * <p>预览与「确认入库 / 重新入库」等用例出入参均为可序列化的 client DTO；策略以 {@code String} 传入由 app 层解析，避免 client 依赖领域枚举。
 *
 * <p>依赖 HTTP multipart 流（文件上传 / 直接解析）与 SSE 进度回调（异步索引）的用例与传输强耦合， 不进对外契约，改由适配层直接调用 app 层执行器，保持 SSE
 * 装配不变。
 *
 * <p>校验失败以 {@link IllegalArgumentException} / {@link IllegalStateException} 抛出，由适配层 或全局异常处理转换为响应。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public interface DocumentIngestionServiceI {

    /** 基于已上传文档解析预览（不入库） */
    PreviewResult previewFromDocument(
            String documentKey, int chunkSize, int overlap, String strategy);

    /** 重新入库已有文档 */
    DocumentVO reindex(String documentKey);

    /** 确认入库（同步，分块 → 向量化 → 存储） */
    DocumentVO index(String documentKey, int chunkSize, int overlap, String strategy);

    /** 使用用户自定义分块入库（同步） */
    DocumentVO indexWithCustomChunks(String documentKey, List<CustomChunkInput> customChunks);
}
