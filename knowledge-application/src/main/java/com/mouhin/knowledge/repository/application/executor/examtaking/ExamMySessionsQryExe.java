package com.mouhin.knowledge.repository.application.executor.examtaking;

import com.mouhin.knowledge.repository.application.converter.ExamTakingConverter;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 我的考试历史查询执行器（app 层用例）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ExamMySessionsQryExe {

    private final ExamSessionGateway examSessionGateway;
    private final ExamTakingSupport support;

    public ExamMySessionsQryExe(ExamSessionGateway examSessionGateway, ExamTakingSupport support) {
        this.examSessionGateway = examSessionGateway;
        this.support = support;
    }

    public List<ExamSessionDTO> execute(String studentToken) {
        Student student = support.resolveStudent(studentToken);
        return examSessionGateway.listByStudentId(student.getId()).stream()
                .map(ExamTakingConverter::toSessionDTO)
                .toList();
    }
}
