package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExtractionCacheDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 解析结果缓存 Mapper（Phase C）。仅用 MyBatis-Plus 通用 CRUD + {@code LambdaQueryWrapper}，无需自定义 SQL。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Mapper
public interface ExtractionCacheMapper extends BaseMapper<ExtractionCacheDO> {}
