package com.mouhin.knowledge.repository.application.executor.document;

import com.mouhin.knowledge.repository.application.converter.DocumentConverter;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 按状态查询文档执行器（app 层用例）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class DocumentListByStatusQryExe {

    private final DocumentGateway documentGateway;

    public DocumentListByStatusQryExe(DocumentGateway documentGateway) {
        this.documentGateway = documentGateway;
    }

    /**
     * @param status 枚举大写名（由适配层预先完成合法性校验）
     */
    public List<DocumentVO> execute(String status) {
        DocumentStatusEnum statusEnum = DocumentStatusEnum.valueOf(status);
        return documentGateway.listByStatus(statusEnum).stream()
                .map(DocumentConverter::toVO)
                .toList();
    }
}
