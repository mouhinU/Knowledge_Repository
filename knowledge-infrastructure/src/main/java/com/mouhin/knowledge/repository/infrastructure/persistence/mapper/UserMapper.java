package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.UserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {}
