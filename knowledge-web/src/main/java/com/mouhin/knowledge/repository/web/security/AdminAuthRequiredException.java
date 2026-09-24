package com.mouhin.knowledge.repository.web.security;

/**
 * 管理端身份缺失或不足以访问当前端点时抛出，由 {@code GlobalExceptionHandler} 统一映射为 HTTP 401。
 *
 * <p>与 {@link IllegalStateException} 区分：后者代表业务状态冲突（409），前者代表鉴权失败（401）， 前端据此可自动跳转登录页；语义混用会导致 401/409
 * 无法辨别，进而影响重连与告警口径。
 *
 * @author mouhinU
 * @date 2026-09-24
 */
public class AdminAuthRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AdminAuthRequiredException(String message) {
        super(message);
    }
}
