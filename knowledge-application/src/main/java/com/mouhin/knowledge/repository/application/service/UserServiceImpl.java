package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.executor.user.UserCreateCmdExe;
import com.mouhin.knowledge.repository.application.executor.user.UserDeleteCmdExe;
import com.mouhin.knowledge.repository.application.executor.user.UserGetQryExe;
import com.mouhin.knowledge.repository.application.executor.user.UserListQryExe;
import com.mouhin.knowledge.repository.application.executor.user.UserUpdateCmdExe;
import com.mouhin.knowledge.repository.client.api.UserServiceI;
import com.mouhin.knowledge.repository.client.dto.UserCreateCmd;
import com.mouhin.knowledge.repository.client.dto.UserUpdateCmd;
import com.mouhin.knowledge.repository.client.dto.UserVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户管理应用服务实现（app 层，仅分发到 Executor）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Service
public class UserServiceImpl implements UserServiceI {

    private final UserListQryExe userListQryExe;
    private final UserGetQryExe userGetQryExe;
    private final UserCreateCmdExe userCreateCmdExe;
    private final UserUpdateCmdExe userUpdateCmdExe;
    private final UserDeleteCmdExe userDeleteCmdExe;

    public UserServiceImpl(UserListQryExe userListQryExe,
                           UserGetQryExe userGetQryExe,
                           UserCreateCmdExe userCreateCmdExe,
                           UserUpdateCmdExe userUpdateCmdExe,
                           UserDeleteCmdExe userDeleteCmdExe) {
        this.userListQryExe = userListQryExe;
        this.userGetQryExe = userGetQryExe;
        this.userCreateCmdExe = userCreateCmdExe;
        this.userUpdateCmdExe = userUpdateCmdExe;
        this.userDeleteCmdExe = userDeleteCmdExe;
    }

    @Override
    public List<UserVO> listUsers() {
        return userListQryExe.execute();
    }

    @Override
    public UserVO getUser(String userKey) {
        return userGetQryExe.execute(userKey);
    }

    @Override
    public UserVO createUser(UserCreateCmd cmd) {
        return userCreateCmdExe.execute(cmd);
    }

    @Override
    public UserVO updateUser(UserUpdateCmd cmd) {
        return userUpdateCmdExe.execute(cmd);
    }

    @Override
    public void deleteUser(String userKey) {
        userDeleteCmdExe.execute(userKey);
    }
}
