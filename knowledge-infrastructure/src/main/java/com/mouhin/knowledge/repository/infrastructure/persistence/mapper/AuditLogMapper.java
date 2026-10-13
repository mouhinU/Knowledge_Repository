package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.AuditLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 平台端审计日志 Mapper（P3-T5）。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogDO> {}
