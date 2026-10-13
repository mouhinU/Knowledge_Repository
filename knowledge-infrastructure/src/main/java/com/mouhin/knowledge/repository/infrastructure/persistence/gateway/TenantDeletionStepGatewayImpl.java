package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.gateway.TenantDeletionStepGateway;
import com.mouhin.knowledge.repository.domain.model.entity.TenantDeletionStep;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.TenantDeletionStepConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantDeletionStepDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.TenantDeletionStepMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@link TenantDeletionStepGateway} MyBatis-Plus 实现（P3-T5）。
 *
 * <p>构造器注入，零 XML；查询走 {@link LambdaQueryWrapper}，写入自动打 {@code createTime/updateTime}。 镜像 {@code
 * TenantProvisionStepGatewayImpl}。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Repository
public class TenantDeletionStepGatewayImpl implements TenantDeletionStepGateway {

    private final TenantDeletionStepMapper mapper;

    public TenantDeletionStepGatewayImpl(TenantDeletionStepMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<TenantDeletionStep> findSuccess(
            Long tenantId, String deletionRequestId, String stepName) {
        LambdaQueryWrapper<TenantDeletionStepDO> wrapper =
                new LambdaQueryWrapper<TenantDeletionStepDO>()
                        .eq(TenantDeletionStepDO::getTenantId, tenantId)
                        .eq(TenantDeletionStepDO::getDeletionRequestId, deletionRequestId)
                        .eq(TenantDeletionStepDO::getStepName, stepName)
                        .eq(TenantDeletionStepDO::getStatus, TenantDeletionStep.STATUS_SUCCESS)
                        .orderByDesc(TenantDeletionStepDO::getStepId)
                        .last("LIMIT 1");
        return Optional.ofNullable(mapper.selectOne(wrapper))
                .map(TenantDeletionStepConverter::toDomain);
    }

    @Override
    public int findMaxAttemptNo(Long tenantId, String deletionRequestId, String stepName) {
        LambdaQueryWrapper<TenantDeletionStepDO> wrapper =
                new LambdaQueryWrapper<TenantDeletionStepDO>()
                        .eq(TenantDeletionStepDO::getTenantId, tenantId)
                        .eq(TenantDeletionStepDO::getDeletionRequestId, deletionRequestId)
                        .eq(TenantDeletionStepDO::getStepName, stepName)
                        .orderByDesc(TenantDeletionStepDO::getAttemptNo)
                        .last("LIMIT 1");
        TenantDeletionStepDO latest = mapper.selectOne(wrapper);
        return latest == null || latest.getAttemptNo() == null ? 0 : latest.getAttemptNo();
    }

    @Override
    public List<TenantDeletionStep> listByRequest(Long tenantId, String deletionRequestId) {
        LambdaQueryWrapper<TenantDeletionStepDO> wrapper =
                new LambdaQueryWrapper<TenantDeletionStepDO>()
                        .eq(TenantDeletionStepDO::getTenantId, tenantId)
                        .eq(TenantDeletionStepDO::getDeletionRequestId, deletionRequestId)
                        .orderByAsc(TenantDeletionStepDO::getStepId);
        return mapper.selectList(wrapper).stream()
                .map(TenantDeletionStepConverter::toDomain)
                .toList();
    }

    @Override
    public void insert(TenantDeletionStep step) {
        TenantDeletionStepDO record = TenantDeletionStepConverter.toDO(step);
        LocalDateTime now = LocalDateTime.now();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        mapper.insert(record);
        step.setStepId(record.getStepId());
        step.setCreateTime(record.getCreateTime());
        step.setUpdateTime(record.getUpdateTime());
    }

    @Override
    public void update(TenantDeletionStep step) {
        TenantDeletionStepDO record = TenantDeletionStepConverter.toDO(step);
        record.setUpdateTime(LocalDateTime.now());
        mapper.updateById(record);
        step.setUpdateTime(record.getUpdateTime());
    }
}
