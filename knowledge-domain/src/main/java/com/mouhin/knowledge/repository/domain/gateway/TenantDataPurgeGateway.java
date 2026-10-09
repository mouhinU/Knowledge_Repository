package com.mouhin.knowledge.repository.domain.gateway;

import java.util.Map;

/**
 * 租户业务数据硬删网关（domain 定义，infrastructure 以 {@code TenantDataPurgeGatewayImpl} 实现，P3-T5）。
 *
 * <p>承载 S-4=方案 A "30 天保留期 + 双人复核通过后硬删租户全量业务行" 的 DB 侧清除能力：按 {@code tenant_id} 跨全部租户业务表 （文档 / 分块 / 图片
 * / 分类 / 用户 / 角色 / 用户角色 / 部门 / 考试 / 学生 / 租户级配置 / 邀请）硬删。 文件层（MinIO）物理清理受 P5-T1 约束延后，本端口只负责 DB 行。
 *
 * <p><b>破坏性纪律</b>：{@link #purgeTenantRows(long)} 不可逆。调用方（{@code TenantDeletionReconciler}）须保证： (1)
 * 已过保留期；(2) 双人复核令牌已消费；(3) 逐步骤幂等（重复调用第二次返回全 0 行）。 提供 {@link #countTenantRows(long)} 供 dry-run
 * 预览，删除前后可对账。
 *
 * <p><b>租户拦截器规避</b>：实现方须对清除语句显式绕过 {@code TenantLineInnerInterceptor}（{@code @InterceptorIgnore}），
 * 否则 {@code saas.enabled=true} 时会被注入 {@code AND tenant_id=<当前上下文>} 而错删 / 漏删。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
public interface TenantDataPurgeGateway {

    /**
     * 统计某租户在各业务表的待删行数（dry-run 预览，<b>不删</b>）。
     *
     * @param tenantId 数值租户主键
     * @return 表名 → 行数，保持稳定插入序（子表在前、主表在后），便于删除前对账
     */
    Map<String, Long> countTenantRows(long tenantId);

    /**
     * 硬删某租户全部业务行（不可逆）。按依赖安全序（子表先于主表）逐表 {@code DELETE ... WHERE tenant_id=?}。
     *
     * <p>幂等：租户已清空时再次调用返回全 0 行，不报错。
     *
     * @param tenantId 数值租户主键
     * @return 表名 → 实际删除行数，保持稳定插入序
     */
    Map<String, Long> purgeTenantRows(long tenantId);
}
