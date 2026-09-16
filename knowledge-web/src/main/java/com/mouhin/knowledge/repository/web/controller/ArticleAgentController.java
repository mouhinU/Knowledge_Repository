package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.service.ArticleGenerationApplicationService;
import com.mouhin.knowledge.repository.domain.model.entity.WritingHistory;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.web.dto.ArticleGenerationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI 文章生成控制器
 * <p>
 * 基于黑板模式的多 Agent 协作，从知识库检索信息并生成文章。
 * 支持 SSE 实时进度推送：
 * <ol>
 *     <li>GET /api/agent/article/progress/{sessionId} → 建立 SSE 连接（先连接）</li>
 *     <li>POST /api/agent/article/generate → 启动生成，返回 sessionId</li>
 * </ol>
 * 前端先建立 SSE 连接，再发送 POST 启动生成，确保不丢失任何进度事件。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-12
 */
@RestController
@RequestMapping("/api/agent")
public class ArticleAgentController {

    private static final Logger logger = LoggerFactory.getLogger(ArticleAgentController.class);

    private final ArticleGenerationApplicationService articleGenerationService;
    private final BlackboardProgressStore progressStore;

    public ArticleAgentController(ArticleGenerationApplicationService articleGenerationService,
                                  BlackboardProgressStore progressStore) {
        this.articleGenerationService = articleGenerationService;
        this.progressStore = progressStore;
    }

    /**
     * 启动文章生成（异步）
     * <p>
     * 立即返回 sessionId，生成过程在后台执行。
     * 进度事件通过已建立的 SSE 连接实时推送。
     * </p>
     *
     * @param request 文章生成请求（包含问题和用户上下文）
     * @return 包含 sessionId 的响应
     */
    @PostMapping("/article/generate")
    public ResponseEntity<Map<String, String>> generateArticle(
            @RequestBody ArticleGenerationRequest request) {

        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        boolean isAdmin = request.getAdmin() != null && request.getAdmin();
        Permission permission = new Permission(
                userId,
                request.getDepartmentId(),
                request.getRoles(),
                isAdmin
        );

        logger.info("收到文章生成请求: question='{}', user='{}'",
                truncate(request.getQuestion(), 50), userId);

        // 使用前端预分配的 sessionId（与 SSE 连接关联）
        String requestedSessionId = request.getSessionId();
        final String sessionId = (requestedSessionId != null && !requestedSessionId.isBlank())
                ? requestedSessionId
                : java.util.UUID.randomUUID().toString();

        // 创建进度回调，绑定到当前 sessionId
        var progressCallback = (com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback)
                event -> progressStore.pushEvent(sessionId, event);

        // 启动异步生成
        articleGenerationService.generateArticleAsync(
                request.getQuestion(), permission, progressCallback, sessionId, request.getCategory());

        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }

    /**
     * SSE 进度流
     * <p>
     * 前端通过 EventSource 连接此端点，实时接收文章生成的进度事件。
     * 事件类型：PHASE / AGENT_OUTPUT / COMPLETED / ERROR
     * </p>
     *
     * @param sessionId 会话 ID
     * @return SSE 事件流
     */
    @GetMapping(value = "/article/progress/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getProgress(@PathVariable String sessionId) {
        logger.debug("SSE 连接建立 [session={}]", sessionId);
        return progressStore.createEmitter(sessionId);
    }

    /**
     * 查询写作历史列表
     *
     * @param limit 最大返回数量，默认 20
     * @return 历史记录列表（按时间倒序）
     */
    @GetMapping("/article/history")
    public ResponseEntity<List<WritingHistory>> listHistory(
            @RequestParam(defaultValue = "20") int limit) {
        List<WritingHistory> history = articleGenerationService.listHistory(limit);
        return ResponseEntity.ok(history);
    }

    /**
     * 查询单条写作历史详情
     *
     * @param sessionId 会话 ID
     * @return 历史记录详情
     */
    @GetMapping("/article/history/{sessionId}")
    public ResponseEntity<WritingHistory> getHistoryDetail(@PathVariable String sessionId) {
        WritingHistory history = articleGenerationService.getHistoryBySessionId(sessionId);
        if (history == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(history);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
