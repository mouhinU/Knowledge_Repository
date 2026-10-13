package com.mouhin.knowledge.repository.client.api;

import com.alibaba.cola.dto.MultiResponse;
import com.alibaba.cola.dto.Response;
import com.alibaba.cola.dto.SingleResponse;
import com.mouhin.knowledge.repository.client.dto.StudentLoginCmd;
import com.mouhin.knowledge.repository.client.dto.StudentRegisterCmd;
import com.mouhin.knowledge.repository.client.dto.StudentVO;

/**
 * 考生认证应用服务契约（client 层）
 *
 * <p>返回值统一采用 COLA 契约类型（{@link SingleResponse} / {@link MultiResponse} / {@link Response}），
 * 适配层负责还原为前端 JSON 形状。注册 / 登录失败以 {@link IllegalArgumentException} 抛出，由适配层转换为 400 响应；令牌校验与按 ID
 * 查询在未命中时返回 data 为 null 的成功响应（不含敏感字段的 {@link StudentVO}）， 由适配层映射为 401 / 404。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public interface StudentServiceI {

    /**
     * 考生注册。
     *
     * @param cmd 注册命令，包含用户名、密码等考生基本信息
     * @return 注册成功后的考生 VO
     * @throws IllegalArgumentException 用户名已存在或参数非法
     */
    SingleResponse<StudentVO> register(StudentRegisterCmd cmd);

    /**
     * 考生登录，签发访问令牌。
     *
     * @param cmd 登录命令，包含用户名与密码
     * @return 登录成功时 {@code data} 为 JWT 访问令牌字符串
     * @throws IllegalArgumentException 账号不存在或密码错误
     */
    SingleResponse<String> login(StudentLoginCmd cmd);

    /**
     * 考生退出登录。
     *
     * @param token 当前有效的访问令牌
     * @return 操作结果响应
     */
    Response logout(String token);

    /**
     * 校验令牌并还原当前登录考生身份。
     *
     * @param token 考生访问令牌
     * @return 令牌有效时返回考生 VO（不含敏感字段）；无效时 {@code data} 为 null
     */
    SingleResponse<StudentVO> validateToken(String token);

    /**
     * 根据 ID 查询考生详情。
     *
     * @param id 考生唯一标识
     * @return 考生 VO，未命中时 {@code data} 为 null
     */
    SingleResponse<StudentVO> getStudentById(Long id);

    /**
     * 查询全部考生列表。
     *
     * @return 全部考生 VO 列表
     */
    MultiResponse<StudentVO> listStudents();
}
