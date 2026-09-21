package com.mouhin.knowledge.repository.application.executor.student;

import com.mouhin.knowledge.repository.application.converter.StudentConverter;
import com.mouhin.knowledge.repository.client.dto.StudentVO;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 考生列表查询执行器（app 层用例）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class StudentListQryExe {

    private final StudentGateway studentGateway;

    public StudentListQryExe(StudentGateway studentGateway) {
        this.studentGateway = studentGateway;
    }

    public List<StudentVO> execute() {
        return studentGateway.listAll().stream().map(StudentConverter::toVO).toList();
    }
}
