package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.service.ExamGenerationApplicationService;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.infrastructure.export.ExamWordExporter;
import com.mouhin.knowledge.repository.web.dto.ExamGenerationRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * AI 试卷生成控制器
 * <p>
 * 提供两种模式：
 * <ul>
 *     <li>异步模式（SSE）：generate-stream + progress → 6 步 Agent 流水线，实时推送进度</li>
 *     <li>同步模式：generate / export-word → 单次 LLM 调用，用于 Word 导出</li>
 * </ul>
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-13
 */
@RestController
@RequestMapping("/api/agent")
public class ExamController {

    private static final Logger logger = LoggerFactory.getLogger(ExamController.class);

    private final ExamGenerationApplicationService examGenerationService;
    private final ExamWordExporter examWordExporter;
    private final BlackboardProgressStore progressStore;

    public ExamController(ExamGenerationApplicationService examGenerationService,
                          ExamWordExporter examWordExporter,
                          BlackboardProgressStore progressStore) {
        this.examGenerationService = examGenerationService;
        this.examWordExporter = examWordExporter;
        this.progressStore = progressStore;
    }

    /**
     * 启动试卷生成（异步，6 步 Agent 流水线）
     * <p>
     * 立即返回 sessionId，生成过程在后台执行。
     * 进度事件通过已建立的 SSE 连接实时推送。
     * </p>
     *
     * @param request 试卷生成请求
     * @return 包含 sessionId 的响应
     */
    @PostMapping("/exam/generate-stream")
    public ResponseEntity<Map<String, String>> generateExamStream(
            @RequestBody ExamGenerationRequest request) {

        if (request.getTopic() == null || request.getTopic().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "考试主题不能为空"));
        }

        if (request.getTotalCount() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "至少需要设置一种题型及数量"));
        }

        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        boolean isAdmin = request.getAdmin() != null && request.getAdmin();
        Permission permission = new Permission(
                userId,
                request.getDepartmentId(),
                request.getRoles(),
                isAdmin
        );

        logger.info("收到试卷生成请求（异步）: topic='{}', difficulty='{}', total={}",
                request.getTopic(), request.getDifficulty(), request.getTotalCount());

        String requestedSessionId = request.getSessionId();
        final String sessionId = (requestedSessionId != null && !requestedSessionId.isBlank())
                ? requestedSessionId
                : java.util.UUID.randomUUID().toString();

        var progressCallback = (com.mouhin.knowledge.repository.domain.service.BlackboardProgressCallback)
                event -> progressStore.pushEvent(sessionId, event);

        String questionConfig = buildQuestionConfig(request);

        examGenerationService.generateExamAsync(
                request.getTopic(),
                request.getDifficulty(),
                questionConfig,
                permission,
                progressCallback,
                sessionId,
                request.getCategory(),
                request.getSchoolLevel());

        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }

    /**
     * SSE 进度流
     * <p>
     * 前端通过 EventSource 连接此端点，实时接收试卷生成的进度事件。
     * 事件类型：PHASE / AGENT_OUTPUT / COMPLETED / ERROR
     * </p>
     *
     * @param sessionId 会话 ID
     * @return SSE 事件流
     */
    @GetMapping(value = "/exam/progress/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getExamProgress(@PathVariable String sessionId) {
        logger.debug("试卷 SSE 连接建立 [session={}]", sessionId);
        return progressStore.createEmitter(sessionId);
    }

    /**
     * 生成试卷（同步，用于 Word 导出）
     */
    @PostMapping("/exam/generate")
    public ResponseEntity<Map<String, String>> generateExam(
            @RequestBody ExamGenerationRequest request) {

        if (request.getTopic() == null || request.getTopic().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "考试主题不能为空"));
        }

        if (request.getTotalCount() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "至少需要设置一种题型及数量"));
        }

        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        boolean isAdmin = request.getAdmin() != null && request.getAdmin();
        Permission permission = new Permission(
                userId,
                request.getDepartmentId(),
                request.getRoles(),
                isAdmin
        );

        logger.info("收到试卷生成请求（同步）: topic='{}', difficulty='{}', total={}",
                request.getTopic(), request.getDifficulty(), request.getTotalCount());

        String examPaper = examGenerationService.generateExam(
                request.getTopic(),
                request.getDifficulty(),
                request.getSchoolLevel(),
                request.getSingleChoiceCount() != null ? request.getSingleChoiceCount() : 0,
                request.getMultiChoiceCount() != null ? request.getMultiChoiceCount() : 0,
                request.getTrueFalseCount() != null ? request.getTrueFalseCount() : 0,
                request.getFillBlankCount() != null ? request.getFillBlankCount() : 0,
                request.getShortAnswerCount() != null ? request.getShortAnswerCount() : 0,
                request.getEssayCount() != null ? request.getEssayCount() : 0,
                permission,
                request.getCategory()
        );

        return ResponseEntity.ok(Map.of("examPaper", examPaper));
    }

    /**
     * 导出试卷为 Word 文档
     */
    @PostMapping("/exam/export-word")
    public void exportExamWord(
            @RequestBody ExamGenerationRequest request,
            HttpServletResponse response) throws Exception {

        if (request.getTopic() == null || request.getTopic().isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "考试主题不能为空");
            return;
        }

        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        boolean isAdmin = request.getAdmin() != null && request.getAdmin();
        Permission permission = new Permission(
                userId,
                request.getDepartmentId(),
                request.getRoles(),
                isAdmin
        );

        logger.info("导出试卷 Word: topic='{}'", request.getTopic());

        String examPaper = examGenerationService.generateExam(
                request.getTopic(),
                request.getDifficulty(),
                request.getSchoolLevel(),
                request.getSingleChoiceCount() != null ? request.getSingleChoiceCount() : 0,
                request.getMultiChoiceCount() != null ? request.getMultiChoiceCount() : 0,
                request.getTrueFalseCount() != null ? request.getTrueFalseCount() : 0,
                request.getFillBlankCount() != null ? request.getFillBlankCount() : 0,
                request.getShortAnswerCount() != null ? request.getShortAnswerCount() : 0,
                request.getEssayCount() != null ? request.getEssayCount() : 0,
                permission,
                request.getCategory()
        );

        String fileName = URLEncoder.encode(request.getTopic() + "_试卷.docx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

        examWordExporter.export(examPaper, response.getOutputStream());
    }

    /**
     * 构建题型配置描述（传递给 Agent）
     */
    private String buildQuestionConfig(ExamGenerationRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getSingleChoiceCount() != null && request.getSingleChoiceCount() > 0) {
            sb.append("单选题 ").append(request.getSingleChoiceCount()).append(" 道、");
        }
        if (request.getMultiChoiceCount() != null && request.getMultiChoiceCount() > 0) {
            sb.append("多选题 ").append(request.getMultiChoiceCount()).append(" 道、");
        }
        if (request.getTrueFalseCount() != null && request.getTrueFalseCount() > 0) {
            sb.append("判断题 ").append(request.getTrueFalseCount()).append(" 道、");
        }
        if (request.getFillBlankCount() != null && request.getFillBlankCount() > 0) {
            sb.append("填空题 ").append(request.getFillBlankCount()).append(" 道、");
        }
        if (request.getShortAnswerCount() != null && request.getShortAnswerCount() > 0) {
            sb.append("简答题 ").append(request.getShortAnswerCount()).append(" 道、");
        }
        if (request.getEssayCount() != null && request.getEssayCount() > 0) {
            sb.append("论述题 ").append(request.getEssayCount()).append(" 道");
        }
        String result = sb.toString();
        return result.endsWith("、") ? result.substring(0, result.length() - 1) : result;
    }

    /**
     * 查询出卷历史列表
     *
     * @param limit 最大返回数量，默认 20
     * @return 历史记录列表（按时间倒序）
     */
    @GetMapping("/exam/history")
    public ResponseEntity<List<ExamHistory>> listExamHistory(
            @RequestParam(defaultValue = "20") int limit) {
        List<ExamHistory> history = examGenerationService.listHistory(limit);
        return ResponseEntity.ok(history);
    }

    /**
     * 分页查询出卷历史列表
     *
     * @param page 页码（从 0 开始），默认 0
     * @param size 每页数量，默认 10
     * @return 分页结果（records / total / page / size）
     */
    @GetMapping("/exam/history/page")
    public ResponseEntity<Map<String, Object>> pageExamHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(examGenerationService.pageHistory(page, size));
    }

    /**
     * 查询单条出卷历史详情
     *
     * @param sessionId 会话 ID
     * @return 历史记录详情
     */
    @GetMapping("/exam/history/{sessionId}")
    public ResponseEntity<ExamHistory> getExamHistoryDetail(@PathVariable String sessionId) {
        ExamHistory history = examGenerationService.getHistoryBySessionId(sessionId);
        if (history == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(history);
    }

    /**
     * 删除出卷历史记录
     *
     * @param sessionId 会话 ID
     * @return 删除结果
     */
    @DeleteMapping("/exam/history/{sessionId}")
    public ResponseEntity<Map<String, String>> deleteExamHistory(@PathVariable String sessionId) {
        examGenerationService.deleteHistory(sessionId);
        return ResponseEntity.ok(Map.of("message", "出卷历史已删除: " + sessionId));
    }

    /**
     * 导出历史试卷为 Word 文档
     *
     * @param sessionId 会话 ID
     * @param response  HTTP 响应
     */
    @PostMapping("/exam/history/{sessionId}/export-word")
    public void exportHistoryWord(@PathVariable String sessionId,
                                  HttpServletResponse response) {
        ExamHistory history = examGenerationService.getHistoryBySessionId(sessionId);
        if (history == null || history.getExamPaper() == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            String topic = history.getTopic() != null ? history.getTopic() : "试卷";
            String filename = URLEncoder.encode(topic + "_试卷.docx", StandardCharsets.UTF_8);

            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);

            examWordExporter.export(history.getExamPaper(), response.getOutputStream());
            response.flushBuffer();

            logger.info("历史试卷 Word 导出完成 [session={}, topic={}]", sessionId, topic);
        } catch (Exception e) {
            logger.error("历史试卷 Word 导出失败 [session={}]", sessionId, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
