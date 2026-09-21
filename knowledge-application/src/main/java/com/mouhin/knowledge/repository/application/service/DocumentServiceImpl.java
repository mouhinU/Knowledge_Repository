package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.executor.document.DocumentArchiveCmdExe;
import com.mouhin.knowledge.repository.application.executor.document.DocumentCategoryStatsQryExe;
import com.mouhin.knowledge.repository.application.executor.document.DocumentDeleteCmdExe;
import com.mouhin.knowledge.repository.application.executor.document.DocumentGetQryExe;
import com.mouhin.knowledge.repository.application.executor.document.DocumentListByDepartmentQryExe;
import com.mouhin.knowledge.repository.application.executor.document.DocumentListByOwnerQryExe;
import com.mouhin.knowledge.repository.application.executor.document.DocumentListByStatusQryExe;
import com.mouhin.knowledge.repository.application.executor.document.DocumentListQryExe;
import com.mouhin.knowledge.repository.application.executor.document.DocumentStatsQryExe;
import com.mouhin.knowledge.repository.client.api.DocumentServiceI;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.client.dto.KnowledgeStatsVO;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 文档管理应用服务实现（app 层，仅分发到 Executor）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Service
public class DocumentServiceImpl implements DocumentServiceI {

    private final DocumentGetQryExe documentGetQryExe;
    private final DocumentListQryExe documentListQryExe;
    private final DocumentListByOwnerQryExe documentListByOwnerQryExe;
    private final DocumentListByDepartmentQryExe documentListByDepartmentQryExe;
    private final DocumentListByStatusQryExe documentListByStatusQryExe;
    private final DocumentStatsQryExe documentStatsQryExe;
    private final DocumentCategoryStatsQryExe documentCategoryStatsQryExe;
    private final DocumentArchiveCmdExe documentArchiveCmdExe;
    private final DocumentDeleteCmdExe documentDeleteCmdExe;

    public DocumentServiceImpl(
            DocumentGetQryExe documentGetQryExe,
            DocumentListQryExe documentListQryExe,
            DocumentListByOwnerQryExe documentListByOwnerQryExe,
            DocumentListByDepartmentQryExe documentListByDepartmentQryExe,
            DocumentListByStatusQryExe documentListByStatusQryExe,
            DocumentStatsQryExe documentStatsQryExe,
            DocumentCategoryStatsQryExe documentCategoryStatsQryExe,
            DocumentArchiveCmdExe documentArchiveCmdExe,
            DocumentDeleteCmdExe documentDeleteCmdExe) {
        this.documentGetQryExe = documentGetQryExe;
        this.documentListQryExe = documentListQryExe;
        this.documentListByOwnerQryExe = documentListByOwnerQryExe;
        this.documentListByDepartmentQryExe = documentListByDepartmentQryExe;
        this.documentListByStatusQryExe = documentListByStatusQryExe;
        this.documentStatsQryExe = documentStatsQryExe;
        this.documentCategoryStatsQryExe = documentCategoryStatsQryExe;
        this.documentArchiveCmdExe = documentArchiveCmdExe;
        this.documentDeleteCmdExe = documentDeleteCmdExe;
    }

    @Override
    public DocumentVO getDocument(String documentKey) {
        return documentGetQryExe.execute(documentKey);
    }

    @Override
    public List<DocumentVO> listByOwner(String ownerId, int page, int size) {
        return documentListByOwnerQryExe.execute(ownerId, page, size);
    }

    @Override
    public List<DocumentVO> listByDepartment(String departmentId, int page, int size) {
        return documentListByDepartmentQryExe.execute(departmentId, page, size);
    }

    @Override
    public List<DocumentVO> listDocuments() {
        return documentListQryExe.execute();
    }

    @Override
    public List<DocumentVO> listByStatus(String status) {
        return documentListByStatusQryExe.execute(status);
    }

    @Override
    public KnowledgeStatsVO getStats() {
        return documentStatsQryExe.execute();
    }

    @Override
    public Map<String, Long> getCategoryStats() {
        return documentCategoryStatsQryExe.execute();
    }

    @Override
    public void archive(String documentKey) {
        documentArchiveCmdExe.execute(documentKey);
    }

    @Override
    public void delete(String documentKey) {
        documentDeleteCmdExe.execute(documentKey);
    }
}
