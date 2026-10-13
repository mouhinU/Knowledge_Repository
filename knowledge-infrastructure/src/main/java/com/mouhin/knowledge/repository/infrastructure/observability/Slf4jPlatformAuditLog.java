package com.mouhin.knowledge.repository.infrastructure.observability;

import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基于 SLF4J 的平台端审计日志实现（infrastructure 层，P3-T1）。
 *
 * <p>把平台管理员对租户的高敏写操作以 {@code key=value} 结构化日志输出，便于 ELK / Loki 采集与合规检索。 参照 {@code
 * Slf4jBlackboardAuditLog} 的脱敏纪律：所有自由文本字段先截断（200 字符）再转义换行 / 引号， 防止日志注入；绝不记录令牌、口令等敏感信息。
 *
 * <p>Phase 4 若需 DB 持久化审计（{@code sys_audit_log}），新增一个实现类并切换注入即可，app 层调用点零改动。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Component
@Slf4j
public class Slf4jPlatformAuditLog implements PlatformAuditLog {

    private static final String LOG_PREFIX = "[PlatformAudit]";

    @Override
    public void logTenantAction(
            String operator, String action, String tenantKey, String outcome, String detail) {
        log.info(
                "{} operator=\"{}\" action={} tenantKey=\"{}\" outcome={} detail=\"{}\"",
                LOG_PREFIX,
                sanitize(operator),
                sanitize(action),
                sanitize(tenantKey),
                sanitize(outcome),
                sanitize(detail));
    }

    /**
     * 清理日志内容，防止日志注入：先截断再转义，避免转义后长度膨胀导致截断错位。
     */
    private String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String truncated = text.substring(0, Math.min(text.length(), 200));
        return truncated.replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"");
    }
}
