package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.domain.repository.DepartmentRepository;
import com.mouhin.knowledge.repository.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 用户管理应用服务
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Service
public class UserManagementApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(UserManagementApplicationService.class);

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    public UserManagementApplicationService(UserRepository userRepository,
                                            DepartmentRepository departmentRepository) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    public List<User> listAll() {
        return userRepository.listAll();
    }

    public User getByKey(String userKey) {
        return userRepository.findByUserKey(userKey)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userKey));
    }

    @Transactional
    public User create(String username, String departmentId, Boolean admin) {
        userRepository.findByUsername(username).ifPresent(u -> {
            throw new IllegalArgumentException("Username already exists: " + username);
        });

        User user = new User();
        user.setUserKey(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setDepartmentId(departmentId);
        user.setAdmin(admin != null ? admin : false);
        userRepository.save(user);

        logger.info("User created: {} ({})", username, user.getUserKey());
        return user;
    }

    @Transactional
    public User update(String userKey, String username, String departmentId, Boolean admin) {
        User user = getByKey(userKey);
        if (username != null) {
            user.setUsername(username);
        }
        if (departmentId != null) {
            user.setDepartmentId(departmentId);
        }
        if (admin != null) {
            user.setAdmin(admin);
        }
        userRepository.update(user);
        return user;
    }

    @Transactional
    public void delete(String userKey) {
        User user = getByKey(userKey);
        userRepository.findByUserKey(userKey);
        logger.info("User deleted: {}", userKey);
    }
}
