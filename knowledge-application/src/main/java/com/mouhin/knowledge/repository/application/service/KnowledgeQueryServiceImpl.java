package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.executor.knowledge.KnowledgeSearchQryExe;
import com.mouhin.knowledge.repository.client.api.KnowledgeQueryServiceI;
import com.mouhin.knowledge.repository.client.dto.SearchCmd;
import com.mouhin.knowledge.repository.client.dto.SearchResponseVO;
import org.springframework.stereotype.Service;

/**
 * 知识库查询应用服务实现（app 层，仅分发到 Executor）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Service
public class KnowledgeQueryServiceImpl implements KnowledgeQueryServiceI {

    private final KnowledgeSearchQryExe knowledgeSearchQryExe;

    public KnowledgeQueryServiceImpl(KnowledgeSearchQryExe knowledgeSearchQryExe) {
        this.knowledgeSearchQryExe = knowledgeSearchQryExe;
    }

    @Override
    public SearchResponseVO search(SearchCmd cmd) {
        return knowledgeSearchQryExe.execute(cmd);
    }
}
