package com.mouhin.knowledge.repository.domain.provisioning;

/**
 * 租户开通步骤 SPI（P3-T2）。
 *
 * <p>每个步骤是一个可幂等重放的最小执行单元，由 app 层 {@code ProvisioningStateMachine} 顺序调度。 步骤实现落 infrastructure 或
 * application 层（视其依赖而定），通过 Spring 注入被状态机收集。
 *
 * <p><b>幂等契约</b>（S-5=方案 A）：同一 {@code (tenantId, provisionRequestId, stepName)} 一旦 {@code SUCCESS}，
 * 重放时状态机会跳过、不再调用 {@link #execute(ProvisioningContext)}；因此步骤实现只需保证"首次执行幂等" （重复调用不产生副作用叠加，如 {@code
 * hasCollection} 前置查、{@code (tenant_id,config_key)} upsert）， 无需自行处理"跨请求去重"。
 *
 * <p><b>失败契约</b>：步骤抛出的任何 {@link RuntimeException} 都会被状态机捕获，写入 {@code error_detail} 并把租户置 {@code
 * FAILED}； 步骤不应吞异常。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
public interface ProvisioningStep {

    /**
     * 步骤名，落 {@code sys_tenant_provision_step.step_name}，须全局唯一且稳定（改名会破坏历史幂等判定）。
     *
     * @return 步骤名常量
     */
    String name();

    /**
     * 执行顺序，越小越先执行。P3-T2 基础步骤从 100 起，P3-T3/T5/T6 新增步骤插入其间。
     *
     * @return 排序权重
     */
    int order();

    /**
     * 执行步骤。首次执行须幂等（重复调用不叠加副作用）。
     *
     * @param context 开通上下文（租户 + 请求 id + 操作者）
     */
    void execute(ProvisioningContext context);
}
