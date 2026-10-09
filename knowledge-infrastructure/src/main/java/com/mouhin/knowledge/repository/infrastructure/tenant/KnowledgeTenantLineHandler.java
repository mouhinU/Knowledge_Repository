package com.mouhin.knowledge.repository.infrastructure.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.mouhin.knowledge.repository.domain.context.TenantContext;
import java.util.Locale;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

/**
 * MyBatis-Plus 租户过滤处理器（P1-T7，SaaS 租户脊柱）。
 *
 * <p>为 {@link com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor} 提供三项能力：
 *
 * <ol>
 *   <li>{@link #getTenantId()}：从 {@link TenantContext} 读出当前线程的 {@code tenant_key}（未绑定则取默认）， 经
 *       {@link TenantIdResolver} 转成数值主键，最终作为 SQL 谓词字面量注入。
 *   <li>{@link #getTenantIdColumn()}：固定列名 {@code tenant_id}，与 V24 起新增的列名严格对齐。
 *   <li>{@link #ignoreTable(String)}：白名单跳过——平台元数据表本身（sys_tenant / sys_tenant_placement）、 开通 /
 *       邀请业务表（sys_tenant_provision_step / sys_user_invite，其 tenant_id 是业务列非过滤维度）、 Flyway 内部表、以及
 *       Phase 4-T3 才补齐 tenant_id 列的 kb_extraction_cache。
 * </ol>
 *
 * <p>不做的事：本类仅在 {@code knowledge.saas.enabled=true} 时被装配；关闭态下 {@code MyBatisPlusConfig} 不注册拦截器，行为与
 * P1-T6 之前的旧版本等价（红线 #11 回退闸门）。多语句 DDL、批处理写入等高级用法在 P1-T10 端到端测试里覆盖。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
public class KnowledgeTenantLineHandler implements TenantLineHandler {

    /**
     * 不参与租户过滤的表名（小写，与 H2 {@code DATABASE_TO_LOWER=TRUE} 及 MySQL 8 默认命名对齐）。
     *
     * <ul>
     *   <li>{@code sys_tenant} / {@code sys_tenant_placement}：平台元数据表，本身就是租户目录；若加过滤会导致自引用悖论。
     *   <li>{@code sys_tenant_provision_step}：平台端开通步骤审计表（P3-T2），其 {@code tenant_id} 是"被开通的租户"
     *       业务列而非过滤维度；若加过滤会与 {@code sys_tenant} 同陷自引用悖论。
     *   <li>{@code sys_tenant_delete_approval} / {@code sys_tenant_deletion_step} / {@code
     *       sys_audit_log}：平台端删除审批令牌 / 删除步骤审计 / 高敏操作审计表（P3-T5），其 {@code tenant_id} / {@code
     *       tenant_key} 均是"被操作的租户"业务列而非过滤维度； 由各自 GatewayImpl 显式追加谓词，同 {@code
     *       sys_tenant_provision_step} 逻辑。
     *   <li>{@code sys_user_invite}：一次性设密邀请表（P3-T3），其 {@code tenant_id} 是"被邀请加入的租户"业务列， 由 {@code
     *       UserInviteGatewayImpl} 显式追加谓词；同 {@code sys_tenant_provision_step} 逻辑。
     *   <li>{@code flyway_schema_history}：Flyway 内部表，DDL 迁移链与租户无关。
     *   <li>{@code kb_extraction_cache}：文档解析结果缓存，Phase 4-T3 才补 tenant_id 列，此处暂不参与过滤。
     * </ul>
     *
     * <p><b>P3-T3 变更</b>：{@code sys_config} 已从本白名单移除——V28 迁移已把该表加 tenant_id 列并改 {@code
     * UNIQUE(tenant_id, config_key)}（C-4=方案 A 提前 P4-T1），从此参与租户过滤。
     */
    public static final Set<String> IGNORE_TABLES =
            Set.of(
                    "sys_tenant",
                    "sys_tenant_placement",
                    "sys_tenant_provision_step",
                    "sys_tenant_delete_approval",
                    "sys_tenant_deletion_step",
                    "sys_audit_log",
                    "sys_user_invite",
                    "flyway_schema_history",
                    "kb_extraction_cache");

    private final TenantIdResolver tenantIdResolver;

    public KnowledgeTenantLineHandler(TenantIdResolver tenantIdResolver) {
        this.tenantIdResolver = tenantIdResolver;
    }

    @Override
    public Expression getTenantId() {
        String key = TenantContext.getOrDefault();
        long id = tenantIdResolver.resolve(key);
        return new LongValue(id);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if (tableName == null) {
            return false;
        }
        // 剥离可能的 schema 前缀（如 `public.sys_user`）与反引号，仅取末段表名。
        String normalized = tableName;
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0) {
            normalized = normalized.substring(dot + 1);
        }
        normalized = normalized.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
        return IGNORE_TABLES.contains(normalized);
    }
}
