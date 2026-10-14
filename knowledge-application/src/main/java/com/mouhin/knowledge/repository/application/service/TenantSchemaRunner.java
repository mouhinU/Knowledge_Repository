package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.domain.constant.AgentNames;
import com.mouhin.knowledge.repository.domain.constant.AuditOutcomes;
import com.mouhin.knowledge.repository.domain.constant.FeatureFlagKeys;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.SiloCollectionAdmin;
import com.mouhin.knowledge.repository.domain.model.valueobject.SiloCollectionSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SILO 集合 schema 对齐巡检器（app 层，P3-T6）。
 *
 * <p>定时 + 手工触发遍历所有 SILO 集合（{@code kc_*}），逐集合检查 schema 与期望模板的一致性，记录审计日志。 单集合检查失败不影响其余集合 ——失败隔离，记
 * {@code FAILED} 审计后继续下一个。
 *
 * <p><b>门控</b>：仅当 {@code knowledge.platform.enabled=true} 才装配本 bean（{@link ConditionalOnProperty}）；
 * 默认关闭态下不装配、不注册定时任务、不触碰 Milvus。
 *
 * <p><b>空跑安全</b>：现网 POOL 态下无任何 SILO 集合，{@link SiloCollectionAdmin#listSiloCollections()} 返回空列表，
 * runner 记审计 {@code NO_SILO_COLLECTION} 后正常退出。
 *
 * <p><b>范围</b>：本类只做<b>检查 + 报告</b>，不执行 schema 变更（alter collection）。schema 变更属 P3-T5 续交范围。
 *
 * @author QoderWork
 * @date 2026-10-12
 */
@Component
@Slf4j
@ConditionalOnProperty(name = FeatureFlagKeys.PLATFORM_ENABLED, havingValue = "true")
public class TenantSchemaRunner {

    /** 审计动作名。 */
    public static final String ACTION_SCHEMA_CHECK = "SILO_SCHEMA_CHECK";

    /** 审计动作名（手工触发）。 */
    public static final String ACTION_SCHEMA_CHECK_MANUAL = "SILO_SCHEMA_CHECK_MANUAL";

    private final SiloCollectionAdmin siloCollectionAdmin;
    private final PlatformAuditLog auditLog;

    /** 期望的向量维度（从配置读取，默认 1024 = bge-m3）。 */
    @Value("${knowledge.milvus.dimension:1024}")
    private int expectedDimension;

    /** 期望的向量字段名（LangChain4j MilvusEmbeddingStore 默认值）。 */
    @Value("${knowledge.saas.silo-schema.expected-vector-field:embedding}")
    private String expectedVectorField;

    public TenantSchemaRunner(SiloCollectionAdmin siloCollectionAdmin, PlatformAuditLog auditLog) {
        this.siloCollectionAdmin = siloCollectionAdmin;
        this.auditLog = auditLog;
    }

    /**
     * 定时巡检：默认每 30 分钟一轮，可经 {@code knowledge.saas.silo-schema.check-interval-ms} 调整。
     *
     * <p>单个集合检查失败不影响其余集合（逐项 try/catch）。
     */
    @Scheduled(fixedDelayString = "${knowledge.saas.silo-schema.check-interval-ms:1800000}")
    public void scheduledCheck() {
        runCheck(ACTION_SCHEMA_CHECK);
    }

    /**
     * 手工触发入口（由 {@code PlatformSchemaHealthController} 调用）。
     *
     * @return 巡检结果摘要
     */
    public SchemaCheckResult manualCheck() {
        return doCheck(ACTION_SCHEMA_CHECK_MANUAL);
    }

    private void runCheck(String action) {
        doCheck(action);
    }

    private SchemaCheckResult doCheck(String action) {
        List<SiloCollectionSchema> collections;
        try {
            collections = siloCollectionAdmin.listSiloCollections();
        } catch (RuntimeException e) {
            log.error("[schema-runner] listSiloCollections failed: {}", e.getMessage(), e);
            auditLog.logTenantAction(
                    AgentNames.ACTOR_SYSTEM,
                    action,
                    "*",
                    AuditOutcomes.FAILED,
                    "listCollections: " + e.getMessage());
            return new SchemaCheckResult(0, 0, 0, List.of("LIST_FAILED: " + e.getMessage()));
        }

        if (collections.isEmpty()) {
            log.debug("[schema-runner] no SILO collections found, idle");
            auditLog.logTenantAction(
                    AgentNames.ACTOR_SYSTEM,
                    action,
                    "*",
                    AuditOutcomes.SUCCESS,
                    "NO_SILO_COLLECTION");
            return new SchemaCheckResult(0, 0, 0, List.of());
        }

        int total = collections.size();
        int aligned = 0;
        int misaligned = 0;
        List<String> issues = new ArrayList<>();

        for (SiloCollectionSchema schema : collections) {
            try {
                List<String> collectionIssues = checkCollection(schema);
                if (collectionIssues.isEmpty()) {
                    aligned++;
                } else {
                    misaligned++;
                    issues.addAll(collectionIssues);
                    auditLog.logTenantAction(
                            AgentNames.ACTOR_SYSTEM,
                            action,
                            schema.tenantKey(),
                            "MISALIGNED",
                            String.join("; ", collectionIssues));
                }
            } catch (RuntimeException e) {
                misaligned++;
                String issue = schema.collectionName() + ": CHECK_ERROR: " + e.getMessage();
                issues.add(issue);
                log.warn(
                        "[schema-runner] check failed for '{}': {}",
                        schema.collectionName(),
                        e.getMessage());
                auditLog.logTenantAction(
                        AgentNames.ACTOR_SYSTEM,
                        action,
                        schema.tenantKey(),
                        AuditOutcomes.FAILED,
                        "check: " + e.getMessage());
            }
        }

        String outcome = misaligned == 0 ? "ALL_ALIGNED" : "MISALIGNED";
        String detail = "total=" + total + " aligned=" + aligned + " misaligned=" + misaligned;
        auditLog.logTenantAction(AgentNames.ACTOR_SYSTEM, action, "*", outcome, detail);
        log.info(
                "[schema-runner] check complete: {} (total={}, aligned={}, misaligned={})",
                outcome,
                total,
                aligned,
                misaligned);

        return new SchemaCheckResult(total, aligned, misaligned, issues);
    }

    /**
     * 检查单个 SILO 集合的 schema 与期望的一致性。
     *
     * @return 不一致项列表；空列表表示完全对齐
     */
    private List<String> checkCollection(SiloCollectionSchema schema) {
        List<String> issues = new ArrayList<>();

        // 检查向量字段存在性
        if (!schema.hasVectorField(expectedVectorField)) {
            issues.add(
                    schema.collectionName()
                            + ": missing expected vector field '"
                            + expectedVectorField
                            + "', actual vector fields: "
                            + schema.vectorFieldNames());
        }

        // 检查动态字段启用
        if (!schema.enableDynamicField()) {
            issues.add(schema.collectionName() + ": dynamic field not enabled (expected true)");
        }

        // 检查分区数 > 0
        if (schema.numOfPartitions() <= 0) {
            issues.add(
                    schema.collectionName()
                            + ": invalid numOfPartitions="
                            + schema.numOfPartitions()
                            + " (expected > 0)");
        }

        return issues;
    }

    /**
     * 巡检结果摘要（供健康端点使用）。
     *
     * @param total 检查的 SILO 集合总数
     * @param aligned 对齐的集合数
     * @param misaligned 不对齐的集合数
     * @param issues 不一致详情列表
     */
    public record SchemaCheckResult(int total, int aligned, int misaligned, List<String> issues) {

        /** 是否全部对齐。 */
        public boolean isAllAligned() {
            return misaligned == 0 && issues.isEmpty();
        }

        /** 转为健康端点响应 Map。 */
        public Map<String, Object> toResponseMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("status", total == 0 ? "IDLE" : (isAllAligned() ? "HEALTHY" : "DEGRADED"));
            map.put("siloCount", total);
            map.put("aligned", aligned);
            map.put("misaligned", misaligned);
            if (!issues.isEmpty()) {
                map.put("issues", issues);
            }
            return map;
        }
    }
}
