package com.mouhin.knowledge.repository.application.executor.user;

import com.mouhin.knowledge.repository.application.converter.UserConverter;
import com.mouhin.knowledge.repository.client.dto.UserVO;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户列表查询执行器（app 层用例）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class UserListQryExe {

    private final UserGateway userGateway;

    public UserListQryExe(UserGateway userGateway) {
        this.userGateway = userGateway;
    }

    public List<UserVO> execute() {
        return userGateway.listAll().stream().map(UserConverter::toVO).toList();
    }
}
