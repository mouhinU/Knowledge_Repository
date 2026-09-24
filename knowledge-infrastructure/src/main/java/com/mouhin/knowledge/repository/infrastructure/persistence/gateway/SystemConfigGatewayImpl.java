package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.gateway.SystemConfigGateway;
import com.mouhin.knowledge.repository.domain.model.entity.SystemConfig;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.SystemConfigConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.SystemConfigDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.SystemConfigMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 系统配置网关实现（基础设施层持久化）
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Repository
public class SystemConfigGatewayImpl implements SystemConfigGateway {

    private final SystemConfigMapper systemConfigMapper;

    public SystemConfigGatewayImpl(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    @Override
    public Optional<SystemConfig> findByKey(String configKey) {
        LambdaQueryWrapper<SystemConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfigDO::getConfigKey, configKey);
        SystemConfigDO doObj = systemConfigMapper.selectOne(wrapper);
        return Optional.ofNullable(SystemConfigConverter.toDomain(doObj));
    }

    @Override
    public List<SystemConfig> findAll() {
        LambdaQueryWrapper<SystemConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SystemConfigDO::getCategory).orderByAsc(SystemConfigDO::getConfigKey);
        return systemConfigMapper.selectList(wrapper).stream()
                .map(SystemConfigConverter::toDomain)
                .toList();
    }

    @Override
    public List<SystemConfig> findByCategory(String category) {
        LambdaQueryWrapper<SystemConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfigDO::getCategory, category).orderByAsc(SystemConfigDO::getConfigKey);
        return systemConfigMapper.selectList(wrapper).stream()
                .map(SystemConfigConverter::toDomain)
                .toList();
    }

    @Override
    public void saveOrUpdate(SystemConfig config) {
        SystemConfigDO doObj = SystemConfigConverter.toDO(config);
        LambdaQueryWrapper<SystemConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfigDO::getConfigKey, config.getConfigKey());
        SystemConfigDO existing = systemConfigMapper.selectOne(wrapper);
        if (existing != null) {
            doObj.setId(existing.getId());
            systemConfigMapper.updateById(doObj);
            config.setId(existing.getId());
        } else {
            systemConfigMapper.insert(doObj);
            config.setId(doObj.getId());
        }
    }

    @Override
    public void batchSaveOrUpdate(List<SystemConfig> configs) {
        for (SystemConfig config : configs) {
            saveOrUpdate(config);
        }
    }
}
