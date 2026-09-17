package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.StudentLoginCmd;
import com.mouhin.knowledge.repository.client.dto.StudentRegisterCmd;
import com.mouhin.knowledge.repository.client.dto.StudentVO;

import java.util.List;
import java.util.Optional;

/**
 * 考生认证应用服务契约（client 层）
 *
 * <p>注册 / 登录失败以 {@link IllegalArgumentException} 抛出，由适配层转换为 400 响应；
 * 令牌校验返回不含敏感字段的 {@link StudentVO}。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public interface StudentServiceI {

    StudentVO register(StudentRegisterCmd cmd);

    String login(StudentLoginCmd cmd);

    void logout(String token);

    Optional<StudentVO> validateToken(String token);

    Optional<StudentVO> getStudentById(Long id);

    List<StudentVO> listStudents();
}
