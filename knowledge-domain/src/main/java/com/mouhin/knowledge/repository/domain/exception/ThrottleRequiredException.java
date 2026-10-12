package com.mouhin.knowledge.repository.domain.exception;

import com.mouhin.knowledge.repository.domain.model.valueobject.QuotaCheckResult;

/**
 * 限流异常（P6-T3 · THROTTLE 策略）。
 *
 * <p>由 {@code QuotaGuard} 在 app 层抛出，adapter 层 {@code GlobalExceptionHandler} 捕获后映射为 HTTP 429 +
 * Retry-After header。放在 domain 层以保持 COLA 依赖方向正确。
 */
public class ThrottleRequiredException extends RuntimeException {

    private final QuotaCheckResult result;

    public ThrottleRequiredException(QuotaCheckResult result) {
        super("Throttle required: " + result.metric().code());
        this.result = result;
    }

    public QuotaCheckResult result() {
        return result;
    }
}
