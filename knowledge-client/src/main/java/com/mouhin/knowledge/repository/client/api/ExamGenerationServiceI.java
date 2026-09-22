package com.mouhin.knowledge.repository.client.api;

import com.alibaba.cola.dto.MultiResponse;
import com.alibaba.cola.dto.PageResponse;
import com.alibaba.cola.dto.Response;
import com.alibaba.cola.dto.SingleResponse;
import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;

/**
 * AI 试卷生成应用服务契约（client 层）
 *
 * <p>仅承载传输无关的出卷历史读写用例，返回值统一采用 COLA 契约类型 （{@link SingleResponse} / {@link MultiResponse} / {@link
 * PageResponse} / {@link Response}）， 适配层负责还原为前端所需 JSON 形状（分页还原为 records / total / page / size；
 * 详情未命中时 data 为 null，由适配层映射为 404）。 试卷 / 方案的生成流程（同步出卷、题型分布、分值平衡、方案校验、异步流水线）依赖领域类型 {@code
 * Permission}、{@code ExamPlan}、{@code BlackboardProgressCallback}、 {@code
 * ScoreRuleEngine.BalanceResult}，刻意不纳入本契约，由 app 层执行器承载并供适配层直接调用， 以保证 client 层不引用 domain 类型，且不改动前端
 * round-trip 的 ExamPlan JSON 形状。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public interface ExamGenerationServiceI {

    MultiResponse<ExamHistoryDTO> listHistory(int limit);

    PageResponse<ExamHistoryDTO> pageHistory(int page, int size);

    SingleResponse<ExamHistoryDTO> getHistoryBySessionId(String sessionId);

    Response deleteHistory(String sessionId);
}
