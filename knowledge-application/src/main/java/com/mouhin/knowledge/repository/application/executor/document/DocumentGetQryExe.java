package com.mouhin.knowledge.repository.application.executor.document;

import com.mouhin.knowledge.repository.application.converter.DocumentConverter;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import org.springframework.stereotype.Component;

/**
 * 文档详情查询执行器（app 层用例）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class DocumentGetQryExe {

    private final DocumentGateway documentGateway;

    public DocumentGetQryExe(DocumentGateway documentGateway) {
        this.documentGateway = documentGateway;
    }

    public DocumentVO execute(String documentKey) {
        Document doc =
                documentGateway
                        .findByDocumentKey(documentKey)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Document not found: " + documentKey));
        return DocumentConverter.toVO(doc);
    }
}
