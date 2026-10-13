package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.UserCreateCmd;
import com.mouhin.knowledge.repository.client.dto.UserUpdateCmd;
import com.mouhin.knowledge.repository.client.dto.UserVO;
import java.util.List;

/**
 * 用户管理应用服务契约（client 层）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public interface UserServiceI {

    /**
     * 查询全部用户列表。
     *
     * @return 全部用户 VO 列表
     */
    List<UserVO> listUsers();

    /**
     * 根据用户标识查询用户详情。
     *
     * @param userKey 用户唯一标识
     * @return 用户 VO，不存在时返回 null
     */
    UserVO getUser(String userKey);

    /**
     * 创建用户。
     *
     * @param cmd 用户创建命令，包含用户名、角色等基本信息
     * @return 创建后的用户 VO
     * @throws IllegalArgumentException 用户名已存在或参数非法
     */
    UserVO createUser(UserCreateCmd cmd);

    /**
     * 更新用户信息。
     *
     * @param cmd 用户更新命令，包含用户标识与待更新字段
     * @return 更新后的用户 VO
     * @throws IllegalArgumentException 用户不存在或参数非法
     */
    UserVO updateUser(UserUpdateCmd cmd);

    /**
     * 删除用户。
     *
     * @param userKey 用户唯一标识
     * @throws IllegalArgumentException 用户不存在
     */
    void deleteUser(String userKey);
}
