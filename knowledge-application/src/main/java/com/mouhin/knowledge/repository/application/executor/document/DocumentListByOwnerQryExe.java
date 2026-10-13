package com.mouhin.knowledge.repository.application.executor.document;

import com.mouhin.knowledge.repository.application.converter.DocumentConverter;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 按所有者分页查询文档执行器（app 层用例）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class DocumentListByOwnerQryExe {

    private final DocumentGateway documentGateway;

    public DocumentListByOwnerQryExe(DocumentGateway documentGateway) {
        this.documentGateway = documentGateway;
    }

    public List<DocumentVO> execute(String ownerId, int page, int size) {
        return documentGateway.listByOwnerId(ownerId, page, size).stream()
                .map(DocumentConverter::toVO)
                .toList();
    }
}
