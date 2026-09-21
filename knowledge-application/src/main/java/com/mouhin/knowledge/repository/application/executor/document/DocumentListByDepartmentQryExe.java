package com.mouhin.knowledge.repository.application.executor.document;

import com.mouhin.knowledge.repository.application.converter.DocumentConverter;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 按部门分页查询文档执行器（app 层用例）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class DocumentListByDepartmentQryExe {

    private final DocumentGateway documentGateway;

    public DocumentListByDepartmentQryExe(DocumentGateway documentGateway) {
        this.documentGateway = documentGateway;
    }

    public List<DocumentVO> execute(String departmentId, int page, int size) {
        return documentGateway.listByDepartmentId(departmentId, page, size).stream()
                .map(DocumentConverter::toVO)
                .toList();
    }
}
