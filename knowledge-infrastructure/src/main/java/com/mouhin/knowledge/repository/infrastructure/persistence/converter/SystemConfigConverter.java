package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.SystemConfig;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.SystemConfigDO;

/**
 * 系统配置 Entity ⇄ DO 转换器
 *
 * @author mouhinU
 * @date 2026-09-23
 */
public final class SystemConfigConverter {

    private SystemConfigConverter() {}

    public static SystemConfig toDomain(SystemConfigDO doObj) {
        if (doObj == null) {
            return null;
        }
        SystemConfig entity = new SystemConfig();
        entity.setId(doObj.getId());
        entity.setConfigKey(doObj.getConfigKey());
        entity.setConfigValue(doObj.getConfigValue());
        entity.setValueType(doObj.getValueType());
        entity.setDescription(doObj.getDescription());
        entity.setCategory(doObj.getCategory());
        entity.setEditable(doObj.getEditable());
        entity.setCreateTime(doObj.getCreateTime());
        entity.setUpdateTime(doObj.getUpdateTime());
        return entity;
    }

    public static SystemConfigDO toDO(SystemConfig entity) {
        if (entity == null) {
            return null;
        }
        SystemConfigDO doObj = new SystemConfigDO();
        doObj.setId(entity.getId());
        doObj.setConfigKey(entity.getConfigKey());
        doObj.setConfigValue(entity.getConfigValue());
        doObj.setValueType(entity.getValueType());
        doObj.setDescription(entity.getDescription());
        doObj.setCategory(entity.getCategory());
        doObj.setEditable(entity.getEditable());
        doObj.setCreateTime(entity.getCreateTime());
        doObj.setUpdateTime(entity.getUpdateTime());
        return doObj;
    }
}
