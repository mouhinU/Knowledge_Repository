package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.UserCreateCmd;
import com.mouhin.knowledge.repository.client.dto.UserUpdateCmd;
import com.mouhin.knowledge.repository.client.dto.UserVO;
import java.util.List;

/**
 * 用户管理应用服务契约（client 层）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public interface UserServiceI {

    List<UserVO> listUsers();

    UserVO getUser(String userKey);

    UserVO createUser(UserCreateCmd cmd);

    UserVO updateUser(UserUpdateCmd cmd);

    void deleteUser(String userKey);
}
