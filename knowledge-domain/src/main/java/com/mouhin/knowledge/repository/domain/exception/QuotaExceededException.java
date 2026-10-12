package com.mouhin.knowledge.repository.domain.exception;

import com.mouhin.knowledge.repository.domain.model.valueobject.QuotaCheckResult;

/**
 * 配额超限异常（P6-T3 · BLOCK 策略）。
 *
 * <p>由 {@code QuotaGuard} 在 app 层抛出，adapter 层 {@code GlobalExceptionHandler} 捕获后映射为 HTTP 402。 放在
 * domain 层以保持 COLA 依赖方向正确（app → domain，adapter → domain）。
 */
public class QuotaExceededException extends RuntimeException {

    private final QuotaCheckResult result;

    public QuotaExceededException(QuotaCheckResult result) {
        super("Quota exceeded: " + result.metric().code());
        this.result = result;
    }

    public QuotaCheckResult result() {
        return result;
    }
}
