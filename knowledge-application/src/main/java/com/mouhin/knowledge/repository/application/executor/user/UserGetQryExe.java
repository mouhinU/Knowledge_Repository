package com.mouhin.knowledge.repository.application.executor.user;

import com.mouhin.knowledge.repository.application.converter.UserConverter;
import com.mouhin.knowledge.repository.client.dto.UserVO;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import org.springframework.stereotype.Component;

/**
 * 单个用户查询执行器（app 层用例）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class UserGetQryExe {

    private final UserGateway userGateway;

    public UserGetQryExe(UserGateway userGateway) {
        this.userGateway = userGateway;
    }

    public UserVO execute(String userKey) {
        User user = userGateway.findByUserKey(userKey)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userKey));
        return UserConverter.toVO(user);
    }
}
