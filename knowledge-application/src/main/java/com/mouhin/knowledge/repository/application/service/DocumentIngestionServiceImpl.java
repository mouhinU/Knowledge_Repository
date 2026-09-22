package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.executor.docingestion.IndexCmdExe;
import com.mouhin.knowledge.repository.application.executor.docingestion.IndexCustomChunksCmdExe;
import com.mouhin.knowledge.repository.application.executor.docingestion.PreviewFromDocumentQryExe;
import com.mouhin.knowledge.repository.application.executor.docingestion.ReindexCmdExe;
import com.mouhin.knowledge.repository.client.api.DocumentIngestionServiceI;
import com.mouhin.knowledge.repository.client.dto.CustomChunkInput;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.client.dto.IndexDocumentCmd;
import com.mouhin.knowledge.repository.client.dto.PreviewDocumentQuery;
import com.mouhin.knowledge.repository.client.dto.PreviewResult;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 文档摄入应用服务实现（app 层，仅分发到执行器）
 *
 * <p>仅承载传输无关的确认类用例。文件上传（multipart）与 SSE 异步索引因与传输强耦合， 由适配层直接调用对应执行器，不经此对外契约。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Service
public class DocumentIngestionServiceImpl implements DocumentIngestionServiceI {

    private final PreviewFromDocumentQryExe previewFromDocumentQryExe;
    private final ReindexCmdExe reindexCmdExe;
    private final IndexCmdExe indexCmdExe;
    private final IndexCustomChunksCmdExe indexCustomChunksCmdExe;

    public DocumentIngestionServiceImpl(
            PreviewFromDocumentQryExe previewFromDocumentQryExe,
            ReindexCmdExe reindexCmdExe,
            IndexCmdExe indexCmdExe,
            IndexCustomChunksCmdExe indexCustomChunksCmdExe) {
        this.previewFromDocumentQryExe = previewFromDocumentQryExe;
        this.reindexCmdExe = reindexCmdExe;
        this.indexCmdExe = indexCmdExe;
        this.indexCustomChunksCmdExe = indexCustomChunksCmdExe;
    }

    @Override
    public PreviewResult previewFromDocument(PreviewDocumentQuery query) {
        return previewFromDocumentQryExe.execute(query);
    }

    @Override
    public DocumentVO reindex(String documentKey) {
        return reindexCmdExe.execute(documentKey);
    }

    @Override
    public DocumentVO index(IndexDocumentCmd cmd) {
        return indexCmdExe.execute(cmd);
    }

    @Override
    public DocumentVO indexWithCustomChunks(
            String documentKey, List<CustomChunkInput> customChunks) {
        return indexCustomChunksCmdExe.execute(documentKey, customChunks);
    }
}
