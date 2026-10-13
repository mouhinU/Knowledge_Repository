package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.LlmProfileDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户 LLM 配置 MyBatis-Plus Mapper（P4-T4）。
 *
 * @author mouhinU
 * @date 2026-10-11
 */
@Mapper
public interface LlmProfileMapper extends BaseMapper<LlmProfileDO> {
}
