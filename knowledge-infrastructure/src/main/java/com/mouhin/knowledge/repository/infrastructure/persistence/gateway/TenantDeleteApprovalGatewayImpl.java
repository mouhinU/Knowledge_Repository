package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.gateway.TenantDeleteApprovalGateway;
import com.mouhin.knowledge.repository.domain.model.entity.TenantDeleteApproval;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.TenantDeleteApprovalConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantDeleteApprovalDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.TenantDeleteApprovalMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link TenantDeleteApprovalGateway} MyBatis-Plus 实现（P3-T5）。
 *
 * <p>构造器注入，零 XML；{@code sys_tenant_delete_approval} 已在 {@code
 * KnowledgeTenantLineHandler#IGNORE_TABLES} 白名单， 故 {@code tenant_id} 谓词由本类显式追加。镜像 {@code
 * UserInviteGatewayImpl} 的一次性码读写风格。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Repository
public class TenantDeleteApprovalGatewayImpl implements TenantDeleteApprovalGateway {

    private final TenantDeleteApprovalMapper mapper;

    public TenantDeleteApprovalGatewayImpl(TenantDeleteApprovalMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<TenantDeleteApproval> findByTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<TenantDeleteApprovalDO> wrapper =
                new LambdaQueryWrapper<TenantDeleteApprovalDO>()
                        .eq(TenantDeleteApprovalDO::getTokenHash, tokenHash)
                        .last("LIMIT 1");
        return Optional.ofNullable(mapper.selectOne(wrapper))
                .map(TenantDeleteApprovalConverter::toDomain);
    }

    @Override
    public List<TenantDeleteApproval> findPendingByTenant(Long tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        LambdaQueryWrapper<TenantDeleteApprovalDO> wrapper =
                new LambdaQueryWrapper<TenantDeleteApprovalDO>()
                        .eq(TenantDeleteApprovalDO::getTenantId, tenantId)
                        .eq(TenantDeleteApprovalDO::getStatus, TenantDeleteApproval.STATUS_PENDING)
                        .orderByDesc(TenantDeleteApprovalDO::getApprovalId);
        return mapper.selectList(wrapper).stream()
                .map(TenantDeleteApprovalConverter::toDomain)
                .toList();
    }

    @Override
    public void insert(TenantDeleteApproval approval) {
        TenantDeleteApprovalDO record = TenantDeleteApprovalConverter.toDO(approval);
        LocalDateTime now = LocalDateTime.now();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        mapper.insert(record);
        approval.setApprovalId(record.getApprovalId());
        approval.setCreateTime(record.getCreateTime());
        approval.setUpdateTime(record.getUpdateTime());
    }

    @Override
    public void update(TenantDeleteApproval approval) {
        TenantDeleteApprovalDO record = TenantDeleteApprovalConverter.toDO(approval);
        record.setUpdateTime(LocalDateTime.now());
        mapper.updateById(record);
        approval.setUpdateTime(record.getUpdateTime());
    }
}
