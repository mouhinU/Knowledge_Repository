package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.SystemConfig;
import java.util.List;
import java.util.Optional;

/**
 * 系统配置网关（领域层端口）
 *
 * @author mouhinU
 * @date 2026-09-23
 */
public interface SystemConfigGateway {

    /** 按配置键查询 */
    Optional<SystemConfig> findByKey(String configKey);

    /** 查询全部配置（按 category + config_key 排序） */
    List<SystemConfig> findAll();

    /** 按分组查询 */
    List<SystemConfig> findByCategory(String category);

    /** 保存或更新配置（按 configKey 幂等） */
    void saveOrUpdate(SystemConfig config);

    /** 批量保存或更新 */
    void batchSaveOrUpdate(List<SystemConfig> configs);
}
