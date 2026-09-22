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

    SingleResponse<StudentVO> register(StudentRegisterCmd cmd);

    SingleResponse<String> login(StudentLoginCmd cmd);

    Response logout(String token);

    SingleResponse<StudentVO> validateToken(String token);

    SingleResponse<StudentVO> getStudentById(Long id);

    MultiResponse<StudentVO> listStudents();
}
