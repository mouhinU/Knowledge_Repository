package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.executor.articlegeneration.GetHistoryBySessionIdQryExe;
import com.mouhin.knowledge.repository.application.executor.articlegeneration.ListHistoryQryExe;
import com.mouhin.knowledge.repository.client.api.ArticleGenerationServiceI;
import com.mouhin.knowledge.repository.client.dto.WritingHistoryDTO;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * AI 文章生成应用服务实现（app 层，仅分发到执行器）
 *
 * <p>权限 / SSE 回调耦合的异步生成路径不经此契约，由适配层直接调用 {@code GenerateArticleAsyncCmdExe}。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Service
public class ArticleGenerationServiceImpl implements ArticleGenerationServiceI {

    private final ListHistoryQryExe listHistoryQryExe;
    private final GetHistoryBySessionIdQryExe getHistoryBySessionIdQryExe;

    public ArticleGenerationServiceImpl(
            ListHistoryQryExe listHistoryQryExe,
            GetHistoryBySessionIdQryExe getHistoryBySessionIdQryExe) {
        this.listHistoryQryExe = listHistoryQryExe;
        this.getHistoryBySessionIdQryExe = getHistoryBySessionIdQryExe;
    }

    @Override
    public List<WritingHistoryDTO> listHistory(int limit) {
        return listHistoryQryExe.execute(limit);
    }

    @Override
    public WritingHistoryDTO getHistoryBySessionId(String sessionId) {
        return getHistoryBySessionIdQryExe.execute(sessionId);
    }
}
