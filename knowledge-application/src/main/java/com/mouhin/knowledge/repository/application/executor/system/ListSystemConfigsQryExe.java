package com.mouhin.knowledge.repository.application.executor.system;

import com.mouhin.knowledge.repository.application.support.SystemConfigService;
import com.mouhin.knowledge.repository.client.dto.SystemConfigDTO;
import com.mouhin.knowledge.repository.domain.model.entity.SystemConfig;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 系统配置列表查询执行器
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
@Slf4j
public class ListSystemConfigsQryExe {

    private final SystemConfigService systemConfigService;

    public ListSystemConfigsQryExe(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    public List<SystemConfigDTO> execute() {
        return systemConfigService.listAll().stream().map(this::toDTO).toList();
    }

    private SystemConfigDTO toDTO(SystemConfig entity) {
        SystemConfigDTO dto = new SystemConfigDTO();
        dto.setId(entity.getId());
        dto.setConfigKey(entity.getConfigKey());
        dto.setConfigValue(entity.getConfigValue());
        dto.setValueType(entity.getValueType());
        dto.setDescription(entity.getDescription());
        dto.setCategory(entity.getCategory());
        dto.setEditable(entity.getEditable());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
}
