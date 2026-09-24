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

    /** 获取系统状态概览（Embedding 模型 / Milvus 连接 / 知识库统计） */
    SystemStatusVO getSystemStatus();

    /** 查询全部系统配置（特性开关 + 运行时参数） */
    List<SystemConfigDTO> listConfigs();

    /** 更新单个配置值 */
    void updateConfig(String configKey, String configValue);
}
