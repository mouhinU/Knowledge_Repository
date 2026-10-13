package com.mouhin.knowledge.repository.web.config;

import com.mouhin.knowledge.repository.domain.exception.QuotaExceededException;
import com.mouhin.knowledge.repository.domain.exception.ThrottleRequiredException;
import com.mouhin.knowledge.repository.domain.model.valueobject.QuotaCheckResult;
import com.mouhin.knowledge.repository.web.security.AdminAuthRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器
 *
 * @author mouhinU
 * @date 2026-09-02
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 统一错误响应 map 的 timestamp 字段名（java:S1192 抽常量防多处漂移）。
     */
    private static final String FIELD_TIMESTAMP = "timestamp";

    /**
     * 统一错误响应 map 的 errorCode 字段名。
     */
    private static final String FIELD_ERROR_CODE = "errorCode";

    /**
     * 统一错误响应 map 的 errorMessage 字段名。
     */
    private static final String FIELD_ERROR_MESSAGE = "errorMessage";

    /**
     * 未知路由对外返回的通用提示；请求路径只落到日志、不回显到响应体（防信息泄露 java:S2092 家族）。
     */
    private static final String MSG_NOT_FOUND = "资源不存在";

    /**
     * 统一错误响应组装；只此一处 Map.of，消除各 handler 的构造重复（java:DuplicatedBlocks）。
     */
    private static ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(
                        Map.of(
                                FIELD_ERROR_CODE,
                                code,
                                FIELD_ERROR_MESSAGE,
                                message,
                                FIELD_TIMESTAMP,
                                LocalDateTime.now().toString()));
    }

    /**
     * 管理端身份缺失 / 非管理员主体：统一映射为 HTTP 401，附 {@code WWW-Authenticate} 提示前端跳登录。 与 {@link
     * IllegalStateException} 分支区分——后者代表业务状态冲突（409），不应与鉴权失败混淆。
     */
    @ExceptionHandler(AdminAuthRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleAdminAuthRequired(
            AdminAuthRequiredException e) {
        log.warn("Admin auth required: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "X-Admin-Token realm=\"knowledge-admin\"")
                .body(
                        Map.of(
                                FIELD_ERROR_CODE,
                                "UNAUTHORIZED",
                                FIELD_ERROR_MESSAGE,
                                "管理员身份未认证或已失效",
                                FIELD_TIMESTAMP,
                                LocalDateTime.now(ZoneId.systemDefault()).toString()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Bad request: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "请求参数不合法");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        log.warn("Conflict: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, "CONFLICT", "当前状态不允许执行此操作");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(
            MaxUploadSizeExceededException e) {
        return error(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "FILE_TOO_LARGE",
                "Upload file exceeds maximum size limit");
    }

    /**
     * 未知路由 → 404。Spring 6 无 handler 抛 {@link NoHandlerFoundException}、静态资源缺失抛 {@link
     * NoResourceFoundException}；两者合并映射，避免掉进通用 {@link Exception} 兜底导致 500 + ERROR
     * 堆栈污染日志。请求路径只落日志、不回显到响应体。
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(HttpServletRequest request) {
        log.warn("404 {} {}", request.getMethod(), request.getRequestURI());
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", MSG_NOT_FOUND);
    }

    /**
     * 配额超限（P6-T3 · BLOCK 策略）→ HTTP 402 Payment Required。
     *
     * <p>响应体含指标 code、限额、当前用量，供前端展示与自动化重试逻辑判定。
     */
    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<Map<String, Object>> handleQuotaExceeded(QuotaExceededException ex) {
        QuotaCheckResult r = ex.result();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(FIELD_ERROR_CODE, "QUOTA_EXCEEDED");
        body.put(FIELD_ERROR_MESSAGE, "配额已用尽：" + r.metric().code());
        body.put("metric", r.metric().code());
        body.put("quota", r.quota());
        body.put("currentUsage", r.currentUsage());
        body.put(FIELD_TIMESTAMP, java.time.Instant.now().toString());
        return ResponseEntity.status(402).body(body);
    }

    /**
     * 限流（P6-T3 · THROTTLE 策略）→ HTTP 429 Too Many Requests + {@code Retry-After} 头。
     *
     * <p>固定 {@code Retry-After: 60} 秒，客户端据此退避重试。
     */
    @ExceptionHandler(ThrottleRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleThrottle(
            ThrottleRequiredException ex, HttpServletResponse response) {
        response.setHeader("Retry-After", "60");
        QuotaCheckResult r = ex.result();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(FIELD_ERROR_CODE, "THROTTLE_REQUIRED");
        body.put(FIELD_ERROR_MESSAGE, "请求过于频繁，请稍后重试");
        body.put("metric", r.metric().code());
        body.put(FIELD_TIMESTAMP, java.time.Instant.now().toString());
        return ResponseEntity.status(429).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }
}
