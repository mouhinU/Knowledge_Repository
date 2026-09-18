package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.executor.examgeneration.DeleteHistoryCmdExe;
import com.mouhin.knowledge.repository.application.executor.examgeneration.GetHistoryBySessionIdQryExe;
import com.mouhin.knowledge.repository.application.executor.examgeneration.ListHistoryQryExe;
import com.mouhin.knowledge.repository.application.executor.examgeneration.PageHistoryQryExe;
import com.mouhin.knowledge.repository.client.api.ExamGenerationServiceI;
import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;
import com.alibaba.cola.dto.MultiResponse;
import com.alibaba.cola.dto.PageResponse;
import com.alibaba.cola.dto.Response;
import com.alibaba.cola.dto.SingleResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AI 试卷生成应用服务实现（app 层，仅分发到执行器）
 *
 * <p>仅承载出卷历史读写用例。试卷 / 方案的生成流程依赖领域类型，由适配层直接调用
 * {@code examgeneration} 包下的生成执行器（GenerateExamAsyncCmdExe 等）。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Service
public class ExamGenerationServiceImpl implements ExamGenerationServiceI {

    private final ListHistoryQryExe listHistoryQryExe;
    private final PageHistoryQryExe pageHistoryQryExe;
    private final GetHistoryBySessionIdQryExe getHistoryBySessionIdQryExe;
    private final DeleteHistoryCmdExe deleteHistoryCmdExe;

    public ExamGenerationServiceImpl(ListHistoryQryExe listHistoryQryExe,
                                     PageHistoryQryExe pageHistoryQryExe,
                                     GetHistoryBySessionIdQryExe getHistoryBySessionIdQryExe,
                                     DeleteHistoryCmdExe deleteHistoryCmdExe) {
        this.listHistoryQryExe = listHistoryQryExe;
        this.pageHistoryQryExe = pageHistoryQryExe;
        this.getHistoryBySessionIdQryExe = getHistoryBySessionIdQryExe;
        this.deleteHistoryCmdExe = deleteHistoryCmdExe;
    }

    /** 分页结果 Map 中记录集合的键（与 {@link PageHistoryQryExe} 输出契约一致） */
    private static final String KEY_RECORDS = "records";
    /** 分页结果 Map 中总记录数的键 */
    private static final String KEY_TOTAL = "total";
    /** 分页结果 Map 中页码的键 */
    private static final String KEY_PAGE = "page";
    /** 分页结果 Map 中每页数量的键 */
    private static final String KEY_SIZE = "size";

    @Override
    public MultiResponse<ExamHistoryDTO> listHistory(int limit) {
        return MultiResponse.of(listHistoryQryExe.execute(limit));
    }

    @Override
    @SuppressWarnings("unchecked")
    public PageResponse<ExamHistoryDTO> pageHistory(int page, int size) {
        Map<String, Object> raw = pageHistoryQryExe.execute(page, size);
        List<ExamHistoryDTO> records = (List<ExamHistoryDTO>) raw.get(KEY_RECORDS);
        int totalCount = toInt(raw.get(KEY_TOTAL));
        int pageIndex = toInt(raw.get(KEY_PAGE));
        int pageSize = toInt(raw.get(KEY_SIZE));
        return PageResponse.of(records, totalCount, pageSize, pageIndex);
    }

    @Override
    public SingleResponse<ExamHistoryDTO> getHistoryBySessionId(String sessionId) {
        return SingleResponse.of(getHistoryBySessionIdQryExe.execute(sessionId));
    }

    @Override
    public Response deleteHistory(String sessionId) {
        deleteHistoryCmdExe.execute(sessionId);
        return Response.buildSuccess();
    }

    /**
     * 将分页 Map 中的数值字段安全转换为 int。
     *
     * @param value Map 中读取的原始值（可能为 Number 或 null）
     * @return int 值；null 时返回 0
     */
    private static int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
