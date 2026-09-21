package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.client.dto.PreviewResult;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.service.DocumentIngestionDomainService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 基于已上传文档解析预览用例执行器（app 层，不入库，使用缓存提取结果）。
 *
 * <p>逻辑原样迁移自 {@code DocumentIngestionApplicationService.previewFromDocument}。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class PreviewFromDocumentQryExe {

    private final DocumentIngestionSupport support;
    private final ExtractionCacheHolder extractionCache;
    private final DocumentGateway documentGateway;
    private final DocumentIngestionDomainService ingestionDomainService;

    public PreviewFromDocumentQryExe(
            DocumentIngestionSupport support,
            ExtractionCacheHolder extractionCache,
            DocumentGateway documentGateway,
            DocumentIngestionDomainService ingestionDomainService) {
        this.support = support;
        this.extractionCache = extractionCache;
        this.documentGateway = documentGateway;
        this.ingestionDomainService = ingestionDomainService;
    }

    public PreviewResult execute(String documentKey, int chunkSize, int overlap, String strategy) {
        Document document =
                documentGateway
                        .findByDocumentKey(documentKey)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Document not found: " + documentKey));

        ExtractionResult extraction = extractionCache.getOrReextract(document);

        ChunkingConfig config =
                support.buildConfig(chunkSize, overlap, support.resolveStrategy(strategy));

        Document tempDoc = new Document();
        tempDoc.setDocumentKey(documentKey);
        tempDoc.setOwnerId(document.getOwnerId());
        tempDoc.setDepartmentId(document.getDepartmentId());
        tempDoc.setVisibility(document.getVisibility());
        tempDoc.setChunkingConfig(config);

        List<DocumentChunk> chunks =
                ingestionDomainService.chunkDocument(tempDoc, extraction.pageTexts(), config);

        return new PreviewResult(
                document.getFileName(),
                document.getFileType(),
                document.getTotalPages() != null && document.getTotalPages() > 0
                        ? document.getTotalPages()
                        : extraction.totalPages(),
                extraction.pageTexts().size(),
                extraction.likelyScanned(),
                extraction.checksum(),
                chunks.size(),
                support.buildPages(extraction.pageTexts()),
                support.buildChunkDetails(chunks));
    }
}
