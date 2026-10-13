package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.SystemConfigDTO;
import com.mouhin.knowledge.repository.client.dto.SystemStatusVO;
import java.util.List;

/**
 * 系统配置应用服务契约（client 层）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public interface SystemConfigServiceI {

    /**
     * 获取系统状态概览（Embedding 模型 / Milvus 连接 / 知识库统计）。
     *
     * @return 系统状态 VO，包含各组件连接状态与知识库统计信息
     */
    SystemStatusVO getSystemStatus();

    /**
     * 查询全部系统配置（特性开关 + 运行时参数）。
     *
     * @return 系统配置 DTO 列表
     */
    List<SystemConfigDTO> listConfigs();

    /**
     * 更新单个配置值。
     *
     * @param configKey 配置项键名
     * @param configValue 配置项新值
     * @throws IllegalArgumentException 配置项不存在或值非法
     */
    void updateConfig(String configKey, String configValue);

    /**
     * 手工失效指定租户的配置缓存（P4-T2 管理端点，仅本租户作用域）。
     *
     * @param tenantKey 租户业务唯一标识
     * @throws IllegalArgumentException 租户不存在
     */
    void invalidateTenantConfig(String tenantKey);
}
