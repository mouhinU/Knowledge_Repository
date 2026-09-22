package com.mouhin.knowledge.repository.application.executor.document;

import com.mouhin.knowledge.repository.client.dto.KnowledgeStatsVO;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import org.springframework.stereotype.Component;

/**
 * 知识库统计查询执行器（app 层用例）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class DocumentStatsQryExe {

    private final DocumentGateway documentGateway;

    public DocumentStatsQryExe(DocumentGateway documentGateway) {
        this.documentGateway = documentGateway;
    }

    public KnowledgeStatsVO execute() {
        long indexed = documentGateway.countByStatus(DocumentStatusEnum.INDEXED);
        long processing = documentGateway.countByStatus(DocumentStatusEnum.PROCESSING);
        long failed = documentGateway.countByStatus(DocumentStatusEnum.FAILED);
        long uploaded = documentGateway.countByStatus(DocumentStatusEnum.UPLOADED);

        KnowledgeStatsVO vo = new KnowledgeStatsVO();
        vo.setTotalDocuments(indexed + processing + failed + uploaded);
        vo.setIndexedDocuments(indexed);
        vo.setProcessingDocuments(processing);
        vo.setFailedDocuments(failed);
        return vo;
    }
}
