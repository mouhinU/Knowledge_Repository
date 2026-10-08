package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.application.service.ProvisioningOutcome;
import com.mouhin.knowledge.repository.application.service.ProvisioningOutcome.StepOutcome;
import com.mouhin.knowledge.repository.client.dto.TenantProvisionVO;
import java.util.List;

/**
 * 开通结果 {@link ProvisioningOutcome} → {@link TenantProvisionVO} 转换器（app 层，P3-T2）。
 *
 * <p>app 层出参边界转换（domain/app 内部记录 → client VO），遵循 COLA 红线 #6。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
public final class TenantProvisionVoConverter {

    private TenantProvisionVoConverter() {}

    /**
     * 转换开通结果。
     *
     * @param tenantKey 目标租户业务键
     * @param outcome 状态机编排结果，不可为 {@code null}
     * @return 开通结果 VO
     */
    public static TenantProvisionVO toVO(String tenantKey, ProvisioningOutcome outcome) {
        TenantProvisionVO vo = new TenantProvisionVO();
        vo.setTenantKey(tenantKey);
        vo.setProvisionRequestId(outcome.provisionRequestId());
        vo.setFinalStatus(outcome.finalStatus());
        vo.setSuccess(outcome.isSuccess());
        vo.setSteps(toStepVos(outcome.steps()));
        return vo;
    }

    private static List<TenantProvisionVO.StepVO> toStepVos(List<StepOutcome> steps) {
        if (steps == null) {
            return List.of();
        }
        return steps.stream()
                .map(
                        s -> {
                            TenantProvisionVO.StepVO stepVo = new TenantProvisionVO.StepVO();
                            stepVo.setStepName(s.stepName());
                            stepVo.setStatus(s.status());
                            stepVo.setAttemptNo(s.attemptNo());
                            stepVo.setErrorDetail(s.errorDetail());
                            return stepVo;
                        })
                .toList();
    }
}
