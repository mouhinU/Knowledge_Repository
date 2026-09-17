package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.UserDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.UserMapper;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.UserRoleMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户仓储实现
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Repository
public class UserGatewayImpl implements UserGateway {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    public UserGatewayImpl(UserMapper userMapper, UserRoleMapper userRoleMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public Optional<User> findById(Long id) {
        UserDO doObj = userMapper.selectById(id);
        return Optional.ofNullable(toDomain(doObj));
    }

    @Override
    public Optional<User> findByUserKey(String userKey) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDO::getUserKey, userKey);
        UserDO doObj = userMapper.selectOne(wrapper);
        return Optional.ofNullable(toDomain(doObj));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        LambdaQueryWrapper<UserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDO::getUsername, username);
        UserDO doObj = userMapper.selectOne(wrapper);
        return Optional.ofNullable(toDomain(doObj));
    }

    @Override
    public List<User> listAll() {
        return userMapper.selectList(null).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<String> findRoleKeysByUserIdAndDocumentId(Long userId, Long documentId) {
        return userRoleMapper.selectRoleKeysByUserId(userId);
    }

    @Override
    public void save(User user) {
        UserDO doObj = toDO(user);
        doObj.setCreateTime(LocalDateTime.now());
        doObj.setUpdateTime(LocalDateTime.now());
        userMapper.insert(doObj);
        user.setId(doObj.getId());
    }

    @Override
    public void update(User user) {
        UserDO doObj = toDO(user);
        doObj.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(doObj);
    }

    private User toDomain(UserDO doObj) {
        if (doObj == null) {
            return null;
        }
        User user = new User();
        user.setId(doObj.getId());
        user.setUserKey(doObj.getUserKey());
        user.setUsername(doObj.getUsername());
        user.setDepartmentId(doObj.getDepartmentId());
        user.setAdmin(doObj.getAdmin());
        user.setCreatedTime(doObj.getCreateTime());
        user.setUpdatedTime(doObj.getUpdateTime());
        return user;
    }

    private UserDO toDO(User user) {
        if (user == null) {
            return null;
        }
        UserDO doObj = new UserDO();
        doObj.setId(user.getId());
        doObj.setUserKey(user.getUserKey());
        doObj.setUsername(user.getUsername());
        doObj.setDepartmentId(user.getDepartmentId());
        doObj.setAdmin(user.getAdmin());
        return doObj;
    }
}
