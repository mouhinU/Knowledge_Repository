package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.User;
import java.util.List;
import java.util.Optional;

/**
 * 用户仓储接口
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
public interface UserGateway {

    Optional<User> findById(Long id);

    Optional<User> findByUserKey(String userKey);

    Optional<User> findByUsername(String username);

    List<User> listAll();

    /** 查找用户在指定文档上拥有的角色列表 */
    List<String> findRoleKeysByUserIdAndDocumentId(Long userId, Long documentId);

    void save(User user);

    void update(User user);
}
