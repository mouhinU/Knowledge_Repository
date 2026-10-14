package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.gateway.TenantProvisionStepGateway;
import com.mouhin.knowledge.repository.domain.model.entity.TenantProvisionStep;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.TenantProvisionStepConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantProvisionStepDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.TenantProvisionStepMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link TenantProvisionStepGateway} MyBatis-Plus 实现（P3-T2）。
 *
 * <p>构造器注入，零 XML；查询走 {@link LambdaQueryWrapper}，写入自动打 {@code createTime/updateTime}。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Repository
public class TenantProvisionStepGatewayImpl implements TenantProvisionStepGateway {

    private final TenantProvisionStepMapper mapper;

    public TenantProvisionStepGatewayImpl(TenantProvisionStepMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<TenantProvisionStep> findSuccess(
            Long tenantId, String provisionRequestId, String stepName) {
        LambdaQueryWrapper<TenantProvisionStepDO> wrapper =
                new LambdaQueryWrapper<TenantProvisionStepDO>()
                        .eq(TenantProvisionStepDO::getTenantId, tenantId)
                        .eq(TenantProvisionStepDO::getProvisionRequestId, provisionRequestId)
                        .eq(TenantProvisionStepDO::getStepName, stepName)
                        .eq(TenantProvisionStepDO::getStatus, TenantProvisionStep.STATUS_SUCCESS)
                        .orderByDesc(TenantProvisionStepDO::getStepId)
                        .last("LIMIT 1");
        return Optional.ofNullable(mapper.selectOne(wrapper))
                .map(TenantProvisionStepConverter.INSTANCE::toDomain);
    }

    @Override
    public int findMaxAttemptNo(Long tenantId, String provisionRequestId, String stepName) {
        LambdaQueryWrapper<TenantProvisionStepDO> wrapper =
                new LambdaQueryWrapper<TenantProvisionStepDO>()
                        .eq(TenantProvisionStepDO::getTenantId, tenantId)
                        .eq(TenantProvisionStepDO::getProvisionRequestId, provisionRequestId)
                        .eq(TenantProvisionStepDO::getStepName, stepName)
                        .orderByDesc(TenantProvisionStepDO::getAttemptNo)
                        .last("LIMIT 1");
        TenantProvisionStepDO latest = mapper.selectOne(wrapper);
        return latest == null || latest.getAttemptNo() == null ? 0 : latest.getAttemptNo();
    }

    @Override
    public List<TenantProvisionStep> listByRequest(Long tenantId, String provisionRequestId) {
        LambdaQueryWrapper<TenantProvisionStepDO> wrapper =
                new LambdaQueryWrapper<TenantProvisionStepDO>()
                        .eq(TenantProvisionStepDO::getTenantId, tenantId)
                        .eq(TenantProvisionStepDO::getProvisionRequestId, provisionRequestId)
                        .orderByAsc(TenantProvisionStepDO::getStepId);
        return mapper.selectList(wrapper).stream()
                .map(TenantProvisionStepConverter.INSTANCE::toDomain)
                .toList();
    }

    @Override
    public void insert(TenantProvisionStep step) {
        TenantProvisionStepDO record = TenantProvisionStepConverter.INSTANCE.toDO(step);
        LocalDateTime now = LocalDateTime.now();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        mapper.insert(record);
        step.setStepId(record.getStepId());
        step.setCreateTime(record.getCreateTime());
        step.setUpdateTime(record.getUpdateTime());
    }

    @Override
    public void update(TenantProvisionStep step) {
        TenantProvisionStepDO record = TenantProvisionStepConverter.INSTANCE.toDO(step);
        record.setUpdateTime(LocalDateTime.now());
        mapper.updateById(record);
        step.setUpdateTime(record.getUpdateTime());
    }
}
