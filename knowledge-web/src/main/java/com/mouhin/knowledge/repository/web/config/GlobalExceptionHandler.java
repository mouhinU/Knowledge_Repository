package com.mouhin.knowledge.repository.web.config;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** 统一错误响应 map 的 timestamp 字段名（java:S1192 抽常量防 4 处漂移）。 */
    private static final String FIELD_TIMESTAMP = "timestamp";

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(
                        Map.of(
                                "errorCode",
                                "BAD_REQUEST",
                                "errorMessage",
                                e.getMessage(),
                                FIELD_TIMESTAMP,
                                LocalDateTime.now().toString()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        log.warn("Conflict: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        Map.of(
                                "errorCode",
                                "CONFLICT",
                                "errorMessage",
                                e.getMessage(),
                                FIELD_TIMESTAMP,
                                LocalDateTime.now().toString()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(
            MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(
                        Map.of(
                                "errorCode",
                                "FILE_TOO_LARGE",
                                "errorMessage",
                                "Upload file exceeds maximum size limit",
                                FIELD_TIMESTAMP,
                                LocalDateTime.now().toString()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        Map.of(
                                "errorCode",
                                "INTERNAL_ERROR",
                                "errorMessage",
                                "An unexpected error occurred",
                                FIELD_TIMESTAMP,
                                LocalDateTime.now().toString()));
    }
}
