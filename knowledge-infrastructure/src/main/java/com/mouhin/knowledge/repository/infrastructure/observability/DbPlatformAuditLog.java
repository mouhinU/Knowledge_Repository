package com.mouhin.knowledge.repository.infrastructure.observability;

import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.AuditLogDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.AuditLogMapper;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * DB 持久化的平台端审计日志实现（infrastructure 层，P3-T5，S-4=方案 A "审计落 {@code sys_audit_log}"）。
 *
 * <p>取代 {@link Slf4jPlatformAuditLog} 成为 {@link PlatformAuditLog} 的首选实现（{@link
 * Primary}）：既把平台管理员对租户生命周期的 高敏写操作落 {@code sys_audit_log} 供合规检索，又同步输出一行结构化日志便于 ELK / Loki 采集。app
 * 层调用点零改动（仍依赖 domain 端口）。
 *
 * <p><b>脱敏纪律</b>（红线 #4 / 安全分册）：所有自由文本字段先截断再转义换行 / 引号，防止日志注入与列宽溢出； 绝不记录令牌、口令等敏感信息。
 *
 * <p><b>可用性纪律</b>：审计落库失败<b>不</b>阻断被审计的业务操作——捕获异常后降级为纯日志并 {@code error} 级告警， 避免因审计表不可用而使归档 /
 * 删除等主流程整体失败（审计缺失由告警侧兜底追查）。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Primary
@Component
@Slf4j
public class DbPlatformAuditLog implements PlatformAuditLog {

    private static final String LOG_PREFIX = "[PlatformAudit]";

    /** {@code detail} 列宽 VARCHAR(500)，与 V29 DDL 对齐；先截断再转义避免转义后膨胀溢出。 */
    private static final int DETAIL_MAX_LENGTH = 500;

    /** 短字段（operator / action / tenantKey / outcome）列宽 VARCHAR(64) 的保守截断上限。 */
    private static final int SHORT_FIELD_MAX_LENGTH = 64;

    private final AuditLogMapper auditLogMapper;

    public DbPlatformAuditLog(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public void logTenantAction(
            String operator, String action, String tenantKey, String outcome, String detail) {
        String safeOperator = sanitize(operator, SHORT_FIELD_MAX_LENGTH);
        String safeAction = sanitize(action, SHORT_FIELD_MAX_LENGTH);
        String safeTenantKey = sanitize(tenantKey, SHORT_FIELD_MAX_LENGTH);
        String safeOutcome = sanitize(outcome, SHORT_FIELD_MAX_LENGTH);
        String safeDetail = sanitize(detail, DETAIL_MAX_LENGTH);

        // 1) 结构化日志（始终输出，作为 DB 落库失败时的兜底证据）
        log.info(
                "{} operator=\"{}\" action={} tenantKey=\"{}\" outcome={} detail=\"{}\"",
                LOG_PREFIX,
                safeOperator,
                safeAction,
                safeTenantKey,
                safeOutcome,
                safeDetail);

        // 2) DB 持久化（失败不阻断主流程，降级为 error 告警）
        try {
            AuditLogDO record = new AuditLogDO();
            record.setOperator(nullIfBlank(safeOperator));
            record.setAction(safeAction);
            record.setTenantKey(nullIfBlank(safeTenantKey));
            record.setOutcome(safeOutcome);
            record.setDetail(nullIfBlank(safeDetail));
            record.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(record);
        } catch (RuntimeException ex) {
            log.error(
                    "{} persist failed action={} tenantKey=\"{}\" outcome={}: {}",
                    LOG_PREFIX,
                    safeAction,
                    safeTenantKey,
                    safeOutcome,
                    ex.getMessage());
        }
    }

    /** 清理内容：先截断至 {@code maxLength} 再转义换行 / 引号，防止日志注入与列宽溢出。 */
    private String sanitize(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String truncated = text.substring(0, Math.min(text.length(), maxLength));
        return truncated.replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"");
    }

    private static String nullIfBlank(String text) {
        return (text == null || text.isEmpty()) ? null : text;
    }
}
