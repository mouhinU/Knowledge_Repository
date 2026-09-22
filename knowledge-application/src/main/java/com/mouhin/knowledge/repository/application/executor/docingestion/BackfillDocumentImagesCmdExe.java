package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 已入库文档图片回填用例执行器（app 层，事务边界）。
 *
 * <p>对既有文档按其持久存储路径重跑一次内嵌图片提取（阶段 1「补抽旧文档」）。文档不存在时抛业务异常； 源文件缺失 / 解析失败由 {@link DocumentImageSupport}
 * 内部吞并记日志，回填按幂等处理（文档内 SHA-256 去重）。
 *
 * @author mouhinU
 * @date 2026-09-20
 */
@Component
@Slf4j
public class BackfillDocumentImagesCmdExe {

    private final DocumentGateway documentGateway;
    private final DocumentImageSupport documentImageSupport;

    public BackfillDocumentImagesCmdExe(
            DocumentGateway documentGateway, DocumentImageSupport documentImageSupport) {
        this.documentGateway = documentGateway;
        this.documentImageSupport = documentImageSupport;
    }

    /**
     * 回填指定文档的图片。
     *
     * @param documentId 文档主键
     * @return 本次新增落库的图片数
     */
    public int execute(Long documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("文档 ID 不能为空");
        }
        Document document =
                documentGateway
                        .findById(documentId)
                        .orElseThrow(() -> new IllegalArgumentException("文档不存在: id=" + documentId));
        int added = documentImageSupport.backfill(document);
        log.info(
                "文档图片回填完成 [documentId={}, documentKey={}, added={}]",
                documentId,
                document.getDocumentKey(),
                added);
        return added;
    }
}
