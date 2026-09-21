package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DocumentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档 Mapper
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Mapper
public interface DocumentMapper extends BaseMapper<DocumentDO> {}
