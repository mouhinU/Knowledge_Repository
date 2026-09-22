package com.mouhin.knowledge.repository.application.executor.articlegeneration;

import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback;
import org.springframework.stereotype.Component;

/**
 * 异步文章生成执行器（权限 / SSE 回调耦合）
 *
 * <p>因入参携带领域类型 {@link Permission} 与 {@link BlackboardProgressCallback}，不纳入 client 层契约， 由适配层在建立 SSE
 * 通道后直接调用。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class GenerateArticleAsyncCmdExe {

    private final ArticleGenerationSupport support;

    public GenerateArticleAsyncCmdExe(ArticleGenerationSupport support) {
        this.support = support;
    }

    public void execute(
            String question,
            Permission permission,
            BlackboardProgressCallback progressCallback,
            String sessionId,
            String category) {
        support.generateArticleAsync(question, permission, progressCallback, sessionId, category);
    }
}
