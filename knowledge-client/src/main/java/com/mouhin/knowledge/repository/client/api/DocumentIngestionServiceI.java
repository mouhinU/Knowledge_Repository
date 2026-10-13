package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.*;
import java.util.List;

/**
 * 文档摄入应用服务契约（client 层，仅承载传输无关的确认类用例）
 *
 * <p>预览与「确认入库 / 重新入库」等用例出入参均为可序列化的 client DTO；分块相关入参（chunkSize / overlap / strategy） 已封装为 {@link
 * PreviewDocumentQuery} / {@link IndexDocumentCmd} 命令查询对象（AGENTS.md §十），策略字符串由 app 层解析，避免 client
 * 依赖领域枚举。
 *
 * <p>依赖 HTTP multipart 流（文件上传 / 直接解析）与 SSE 进度回调（异步索引）的用例与传输强耦合， 不进对外契约，改由适配层直接调用 app 层执行器，保持 SSE
 * 装配不变。
 *
 * <p>校验失败以 {@link IllegalArgumentException} / {@link IllegalStateException} 抛出，由适配层 或全局异常处理转换为响应。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public interface DocumentIngestionServiceI {

    /**
     * 基于已上传文档进行解析预览（不入库）。
     *
     * @param query 预览查询参数，包含文档标识、分块策略等配置
     * @return 解析预览结果，包含分块文本与元数据
     * @throws IllegalArgumentException 文档不存在或参数非法
     */
    PreviewResult previewFromDocument(PreviewDocumentQuery query);

    /**
     * 重新入库已有文档（使用原有分块配置重新向量化）。
     *
     * @param documentKey 文档唯一标识
     * @return 重新入库后的文档 VO
     * @throws IllegalArgumentException 文档不存在
     */
    DocumentVO reindex(String documentKey);

    /**
     * 确认入库（同步执行：分块 → 向量化 → 存储）。
     *
     * @param cmd 入库命令，包含文档标识、分块大小、重叠量与分块策略
     * @return 入库完成后的文档 VO
     * @throws IllegalArgumentException 文档不存在或参数非法
     * @throws IllegalStateException 文档状态不允许入库
     */
    DocumentVO index(IndexDocumentCmd cmd);

    /**
     * 使用用户自定义分块内容入库（同步执行）。
     *
     * @param documentKey 文档唯一标识
     * @param customChunks 用户自定义的分块内容列表
     * @return 入库完成后的文档 VO
     * @throws IllegalArgumentException 文档不存在或分块列表为空
     */
    DocumentVO indexWithCustomChunks(String documentKey, List<CustomChunkInput> customChunks);
}
