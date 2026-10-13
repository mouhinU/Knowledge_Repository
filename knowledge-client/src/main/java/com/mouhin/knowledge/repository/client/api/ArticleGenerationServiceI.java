package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.WritingHistoryDTO;
import java.util.List;

/**
 * AI 文章生成应用服务契约（client 层）
 *
 * <p>仅承载传输无关的历史查询用例。黑板模式异步生成流程依赖领域类型 {@code Permission} 与 {@code
 * BlackboardProgressCallback}，刻意不纳入本契约， 由 app 层执行器承载并供适配层直接调用，以保证 client 层不引用 domain 类型。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public interface ArticleGenerationServiceI {

    /**
     * 查询文章写作历史记录列表。
     *
     * @param limit 返回的最大记录条数
     * @return 写作历史列表，按时间倒序排列
     */
    List<WritingHistoryDTO> listHistory(int limit);

    /**
     * 根据会话 ID 查询单条文章写作历史。
     *
     * @param sessionId 写作会话唯一标识
     * @return 对应的写作历史 DTO，未命中时返回 null
     */
    WritingHistoryDTO getHistoryBySessionId(String sessionId);
}
