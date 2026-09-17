package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.WritingHistoryDTO;

import java.util.List;

/**
 * AI 文章生成应用服务契约（client 层）
 *
 * <p>仅承载传输无关的历史查询用例。黑板模式异步生成流程依赖领域类型
 * {@code Permission} 与 {@code BlackboardProgressCallback}，刻意不纳入本契约，
 * 由 app 层执行器承载并供适配层直接调用，以保证 client 层不引用 domain 类型。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public interface ArticleGenerationServiceI {

    List<WritingHistoryDTO> listHistory(int limit);

    WritingHistoryDTO getHistoryBySessionId(String sessionId);
}
