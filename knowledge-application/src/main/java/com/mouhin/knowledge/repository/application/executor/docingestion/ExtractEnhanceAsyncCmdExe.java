package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 视觉异步增强用例执行器（app 层，Phase C）。
 *
 * <p>预览默认先返回 PDFBox 原生文本层（快），本执行器在后台以 {@code visionExecutor} 线程池跑混合解析补全疑似扫描页： 视觉 HTTP
 * 与持久缓存写入全在异步线程完成， 不阻塞用户、<b>不置于数据库事务</b>（AGENTS.md 红线 #8）。完成后把增强结果回填进程内 {@link
 * ExtractionCacheHolder}， 使后续预览/入库读到补全文本。是否触发由配置 {@code knowledge.extractor.vision.enabled}
 * 与调用方决定；默认关闭时混合策略 {@code supports()} 恒 false， 增强等价于一次原生解析。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
@Slf4j
public class ExtractEnhanceAsyncCmdExe {

    private final DocumentGateway documentGateway;
    private final DocumentExtractionGateway documentExtractionService;
    private final ExtractionCacheHolder extractionCache;
    private final Executor visionExecutor;

    public ExtractEnhanceAsyncCmdExe(
            DocumentGateway documentGateway,
            DocumentExtractionGateway documentExtractionService,
            ExtractionCacheHolder extractionCache,
            @Qualifier("visionExecutor") Executor visionExecutor) {
        this.documentGateway = documentGateway;
        this.documentExtractionService = documentExtractionService;
        this.extractionCache = extractionCache;
        this.visionExecutor = visionExecutor;
    }

    /**
     * 提交后台视觉增强，立即返回（fire-and-forget）。
     *
     * @param documentKey 文档标识
     * @return 异步任务句柄（仅便于测试/编排观察完成，调用方可忽略）
     */
    public CompletableFuture<Void> execute(String documentKey) {
        return execute(documentKey, List.of());
    }

    /**
     * 提交后台视觉增强（可强制解析策略栈），立即返回。
     *
     * @param documentKey 文档标识
     * @param forcedStack 强制策略名栈（已白名单校验）；为空走配置默认路由
     * @return 异步任务句柄
     */
    public CompletableFuture<Void> execute(String documentKey, List<String> forcedStack) {
        return CompletableFuture.runAsync(() -> enhance(documentKey, forcedStack), visionExecutor);
    }

    /**
     * 同步执行增强核心（供 {@link #execute} 异步调用，亦便于单测直接驱动）。
     *
     * @param documentKey 文档标识
     * @return 增强后的提取结果；文档或存储文件不存在时返回 {@code null}
     */
    public ExtractionResult enhance(String documentKey) {
        return enhance(documentKey, List.of());
    }

    /**
     * 同步执行增强核心（可强制解析策略栈）。
     *
     * @param documentKey 文档标识
     * @param forcedStack 强制策略名栈（已白名单校验）；为空走配置默认路由
     * @return 增强后的提取结果；文档或存储文件不存在时返回 {@code null}
     */
    public ExtractionResult enhance(String documentKey, List<String> forcedStack) {
        Document document = documentGateway.findByDocumentKey(documentKey).orElse(null);
        if (document == null) {
            log.warn("vision enhance skipped: document not found {}", documentKey);
            return null;
        }
        Path storagePath = Path.of(document.getStoragePath());
        if (!Files.exists(storagePath)) {
            log.warn("vision enhance skipped: stored file missing {}", document.getStoragePath());
            return null;
        }
        try {
            long size = Files.size(storagePath);
            ExtractionResult result =
                    (forcedStack == null || forcedStack.isEmpty())
                            ? documentExtractionService.extractText(
                                    storagePath, size, document.getFileName())
                            : documentExtractionService.extractText(
                                    storagePath, size, document.getFileName(), forcedStack);
            extractionCache.put(documentKey, result);
            log.info(
                    "vision enhance done for {}: {} pages, format={}",
                    documentKey,
                    result.totalPages(),
                    result.detectedFormat());
            return result;
        } catch (IOException e) {
            log.error("vision enhance failed for {}: {}", documentKey, e.getMessage(), e);
            return null;
        }
    }
}
