package com.mouhin.knowledge.repository.application.executor.student;

import com.mouhin.knowledge.repository.application.converter.StudentConverter;
import com.mouhin.knowledge.repository.client.dto.StudentVO;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 按 ID 查询考生执行器（app 层用例）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class StudentGetByIdQryExe {

    private final StudentGateway studentGateway;

    public StudentGetByIdQryExe(StudentGateway studentGateway) {
        this.studentGateway = studentGateway;
    }

    public Optional<StudentVO> execute(Long id) {
        return studentGateway.findById(id).map(StudentConverter::toVO);
    }
}
