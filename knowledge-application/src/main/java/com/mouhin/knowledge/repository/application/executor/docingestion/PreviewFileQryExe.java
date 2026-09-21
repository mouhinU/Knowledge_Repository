package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.client.dto.PreviewResult;
import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.service.DocumentIngestionDomainService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 直接解析上传文件预览用例执行器（app 层，不入库）。
 *
 * <p>逻辑原样迁移自 {@code DocumentIngestionApplicationService.preview(MultipartFile,...)}。出入参含 {@link
 * MultipartFile}（传输耦合），当前无适配层调用点，作为完整用例保留。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class PreviewFileQryExe {

    private final DocumentIngestionSupport support;
    private final DocumentExtractionGateway documentExtractionService;
    private final DocumentIngestionDomainService ingestionDomainService;

    public PreviewFileQryExe(
            DocumentIngestionSupport support,
            DocumentExtractionGateway documentExtractionService,
            DocumentIngestionDomainService ingestionDomainService) {
        this.support = support;
        this.documentExtractionService = documentExtractionService;
        this.ingestionDomainService = ingestionDomainService;
    }

    public PreviewResult execute(MultipartFile file, int chunkSize, int overlap, String strategy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        Path tempFile;
        try {
            tempFile = support.saveToTemp(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save uploaded file", e);
        }

        try {
            ExtractionResult extraction =
                    documentExtractionService.extractText(
                            tempFile, file.getSize(), file.getOriginalFilename());

            ChunkingConfig config =
                    support.buildConfig(chunkSize, overlap, support.resolveStrategy(strategy));

            Document tempDoc = new Document();
            tempDoc.setDocumentKey("preview");
            tempDoc.setOwnerId("preview");
            tempDoc.setDepartmentId("preview");
            tempDoc.setVisibility(DocumentVisibilityEnum.INTERNAL);
            tempDoc.setChunkingConfig(config);

            List<DocumentChunk> chunks =
                    ingestionDomainService.chunkDocument(tempDoc, extraction.pageTexts(), config);

            return new PreviewResult(
                    file.getOriginalFilename(),
                    extraction.detectedFormat(),
                    extraction.totalPages(),
                    extraction.pageTexts().size(),
                    extraction.likelyScanned(),
                    extraction.checksum(),
                    chunks.size(),
                    support.buildPages(extraction.pageTexts()),
                    support.buildChunkDetails(chunks));

        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract text: " + e.getMessage(), e);
        } finally {
            support.deleteTempFile(tempFile);
        }
    }
}
