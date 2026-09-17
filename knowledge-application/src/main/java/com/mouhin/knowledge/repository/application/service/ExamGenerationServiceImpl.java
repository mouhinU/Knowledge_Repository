package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.executor.examgeneration.DeleteHistoryCmdExe;
import com.mouhin.knowledge.repository.application.executor.examgeneration.GetHistoryBySessionIdQryExe;
import com.mouhin.knowledge.repository.application.executor.examgeneration.ListHistoryQryExe;
import com.mouhin.knowledge.repository.application.executor.examgeneration.PageHistoryQryExe;
import com.mouhin.knowledge.repository.client.api.ExamGenerationServiceI;
import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;
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

    @Override
    public List<ExamHistoryDTO> listHistory(int limit) {
        return listHistoryQryExe.execute(limit);
    }

    @Override
    public Map<String, Object> pageHistory(int page, int size) {
        return pageHistoryQryExe.execute(page, size);
    }

    @Override
    public ExamHistoryDTO getHistoryBySessionId(String sessionId) {
        return getHistoryBySessionIdQryExe.execute(sessionId);
    }

    @Override
    public void deleteHistory(String sessionId) {
        deleteHistoryCmdExe.execute(sessionId);
    }
}
