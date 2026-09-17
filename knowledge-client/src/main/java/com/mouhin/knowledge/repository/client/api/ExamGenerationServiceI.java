package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;

import java.util.List;
import java.util.Map;

/**
 * AI 试卷生成应用服务契约（client 层）
 *
 * <p>仅承载传输无关的出卷历史读写用例，返回 DTO。
 * 试卷 / 方案的生成流程（同步出卷、题型分布、分值平衡、方案校验、异步流水线）依赖领域类型
 * {@code Permission}、{@code ExamPlan}、{@code BlackboardProgressCallback}、
 * {@code ScoreRuleEngine.BalanceResult}，刻意不纳入本契约，由 app 层执行器承载并供适配层直接调用，
 * 以保证 client 层不引用 domain 类型，且不改动前端 round-trip 的 ExamPlan JSON 形状。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public interface ExamGenerationServiceI {

    List<ExamHistoryDTO> listHistory(int limit);

    Map<String, Object> pageHistory(int page, int size);

    ExamHistoryDTO getHistoryBySessionId(String sessionId);

    void deleteHistory(String sessionId);
}
