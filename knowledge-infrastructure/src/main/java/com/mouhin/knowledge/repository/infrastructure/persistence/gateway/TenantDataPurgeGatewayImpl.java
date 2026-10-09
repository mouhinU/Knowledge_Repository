package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.mouhin.knowledge.repository.domain.gateway.TenantDataPurgeGateway;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.TenantPurgeMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * {@link TenantDataPurgeGateway} 实现（infrastructure 层，P3-T5 租户硬删 DB 侧）。
 *
 * <p>按 {@link #PURGE_TABLES} 的<b>子表先于主表</b>安全序，对每张租户业务表执行 {@code DELETE ... WHERE tenant_id=?}。
 * 表名集是硬编码白名单 （{@link #WHITELIST}），杜绝红线 #5 的动态标识符注入风险；{@code tenant_id} 走 {@code #{}} 绑定。
 *
 * <p><b>幂等</b>：删除是"按 tenant_id 清空"，重复调用第二次自然返回全 0 行，不报错——契合 {@code TenantDeletionReconciler}
 * 的步骤级续跑语义。
 *
 * <p><b>范围</b>：仅 DB 业务行。文件层（MinIO）物理清理受 P5-T1 约束延后；无 {@code tenant_id} 列的表（如 {@code chat_memory}）
 * 不在本清单，留待后续按租户维度可寻址时补齐。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Repository
@Slf4j
public class TenantDataPurgeGatewayImpl implements TenantDataPurgeGateway {

    /**
     * 待清除的租户业务表，按"子表先于主表"排序（无外键约束，但保持此序以便未来加约束时不破坏）。
     *
     * <p>与 V24/V25/V26（业务表补 {@code tenant_id}）+ V28（{@code sys_config} / {@code sys_user_invite}
     * 租户化）对齐。
     */
    private static final List<String> PURGE_TABLES =
            List.of(
                    // 文档域：分块 / 图片 先于文档，分类最后
                    "kb_document_chunk",
                    "kb_document_image",
                    "kb_document",
                    "kb_category",
                    // 考试域：答题 / 题目 / 场次 先于历史，学生场次先于学生
                    "kb_exam_answer",
                    "kb_exam_question",
                    "kb_exam_session",
                    "kb_exam_history",
                    "kb_student_session",
                    "kb_student",
                    "kb_writing_history",
                    // 组织域：用户角色关联先于用户 / 角色，租户级配置与邀请最后
                    "sys_user_role",
                    "sys_user",
                    "sys_role",
                    "sys_department",
                    "sys_config",
                    "sys_user_invite");

    /** 表名白名单（{@link #PURGE_TABLES} 的集合视图），用于删除前的防御性校验。 */
    private static final Set<String> WHITELIST = Set.copyOf(PURGE_TABLES);

    private final TenantPurgeMapper purgeMapper;

    public TenantDataPurgeGatewayImpl(TenantPurgeMapper purgeMapper) {
        this.purgeMapper = purgeMapper;
    }

    @Override
    public Map<String, Long> countTenantRows(long tenantId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : PURGE_TABLES) {
            assertWhitelisted(table);
            counts.put(table, purgeMapper.countByTenant(table, tenantId));
        }
        return counts;
    }

    @Override
    public Map<String, Long> purgeTenantRows(long tenantId) {
        Map<String, Long> deleted = new LinkedHashMap<>();
        long total = 0L;
        for (String table : PURGE_TABLES) {
            assertWhitelisted(table);
            long rows = purgeMapper.deleteByTenant(table, tenantId);
            deleted.put(table, rows);
            total += rows;
        }
        log.info(
                "Purged {} business rows for tenant {} across {} tables",
                total,
                tenantId,
                PURGE_TABLES.size());
        return deleted;
    }

    /** 防御性白名单校验：表名必须来自硬编码常量集，否则拒绝执行（红线 #5）。 */
    private static void assertWhitelisted(String table) {
        if (!WHITELIST.contains(table)) {
            throw new IllegalArgumentException("Table not in purge whitelist: " + table);
        }
    }
}
