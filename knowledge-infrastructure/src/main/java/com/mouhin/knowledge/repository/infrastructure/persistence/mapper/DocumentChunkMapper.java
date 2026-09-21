package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DocumentChunkDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档分块 Mapper
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunkDO> {}
