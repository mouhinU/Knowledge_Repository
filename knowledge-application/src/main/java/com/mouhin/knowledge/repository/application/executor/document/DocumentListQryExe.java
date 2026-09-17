package com.mouhin.knowledge.repository.application.executor.document;

import com.mouhin.knowledge.repository.application.converter.DocumentConverter;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 全部文档列表查询执行器（app 层用例）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class DocumentListQryExe {

    private final DocumentGateway documentGateway;

    public DocumentListQryExe(DocumentGateway documentGateway) {
        this.documentGateway = documentGateway;
    }

    public List<DocumentVO> execute() {
        return documentGateway.listAll().stream().map(DocumentConverter::toVO).toList();
    }
}
