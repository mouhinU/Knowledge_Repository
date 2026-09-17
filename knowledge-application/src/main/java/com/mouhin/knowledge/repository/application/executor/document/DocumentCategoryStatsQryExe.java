package com.mouhin.knowledge.repository.application.executor.document;

import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 各分类文档数量统计执行器（app 层用例）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class DocumentCategoryStatsQryExe {

    private final DocumentGateway documentGateway;

    public DocumentCategoryStatsQryExe(DocumentGateway documentGateway) {
        this.documentGateway = documentGateway;
    }

    public Map<String, Long> execute() {
        return documentGateway.countByCategory();
    }
}
