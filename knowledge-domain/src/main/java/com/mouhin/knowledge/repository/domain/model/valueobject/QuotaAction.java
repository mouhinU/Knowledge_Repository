package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * QuotaGuard 检查后的处置动作（P6-T3）。
 *
 * <p>由 {@link QuotaPolicy} 映射而来，增加 {@link #PASS} 表示未超额。
 */
public enum QuotaAction {
    PASS,
    ALERT,
    THROTTLE,
    BLOCK
}
