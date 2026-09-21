package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.client.dto.ExamDocumentImageVO;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentImage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 文档配图列表查询执行器（app 层）。
 *
 * <p>返回某文档已提取并落库的图片清单（含展示 URL 与元信息），供管理端校对选图界面渲染缩略图网格。 文档不存在时返回空列表。
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
@Component
public class ListDocumentImagesQryExe {

    private static final Logger logger = LoggerFactory.getLogger(ListDocumentImagesQryExe.class);

    private final DocumentGateway documentGateway;
    private final DocumentImageSupport documentImageSupport;

    public ListDocumentImagesQryExe(
            DocumentGateway documentGateway, DocumentImageSupport documentImageSupport) {
        this.documentGateway = documentGateway;
        this.documentImageSupport = documentImageSupport;
    }

    public List<ExamDocumentImageVO> execute(Long documentId) {
        if (documentId == null) {
            return List.of();
        }
        Document document = documentGateway.findById(documentId).orElse(null);
        if (document == null) {
            logger.debug("文档不存在，返回空图片列表 [documentId={}]", documentId);
            return List.of();
        }
        return documentImageSupport.listByDocument(document).stream()
                .map(ListDocumentImagesQryExe::toVO)
                .toList();
    }

    private static ExamDocumentImageVO toVO(DocumentImage image) {
        ExamDocumentImageVO vo = new ExamDocumentImageVO();
        vo.setAssetKey(image.getAssetKey());
        vo.setDocumentId(image.getDocumentId());
        vo.setDocumentKey(image.getDocumentKey());
        vo.setUrl("/api/exam/assets/" + image.getAssetKey());
        vo.setPageNo(image.getPageNo());
        vo.setSeqOnPage(image.getSeqOnPage());
        vo.setMimeType(image.getMimeType());
        vo.setWidth(image.getWidth());
        vo.setHeight(image.getHeight());
        vo.setByteSize(image.getByteSize());
        return vo;
    }
}
