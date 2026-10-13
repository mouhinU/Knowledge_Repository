package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/** 套餐限额视图对象（P6-T2）。 */
@Getter
@Setter
public class TenantPlanLimitVO {
    private String tenantKey;
    private String metric;
    private Long quota;
    private String policy;
}
