package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.client.dto.StudentVO;
import com.mouhin.knowledge.repository.domain.model.entity.Student;

/**
 * 考生 领域实体 → 视图对象 转换器（app 层）
 *
 * <p>映射与原 {@code StudentAuthController.me} 一致：displayName null→username， studentNo null→""。不输出密码 /
 * 令牌字段。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public final class StudentConverter {

    private StudentConverter() {}

    public static StudentVO toVO(Student student) {
        StudentVO vo = new StudentVO();
        vo.setStudentId(student.getId());
        vo.setUsername(student.getUsername());
        vo.setDisplayName(
                student.getDisplayName() != null
                        ? student.getDisplayName()
                        : student.getUsername());
        vo.setStudentNo(student.getStudentNo() != null ? student.getStudentNo() : "");
        return vo;
    }
}
