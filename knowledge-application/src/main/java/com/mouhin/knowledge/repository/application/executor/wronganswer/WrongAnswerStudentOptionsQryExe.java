package com.mouhin.knowledge.repository.application.executor.wronganswer;

import com.mouhin.knowledge.repository.application.converter.WrongAnswerConverter;
import com.mouhin.knowledge.repository.client.dto.StudentOptionVO;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 考生下拉选项查询执行器（app 层用例，供错题本筛选使用）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class WrongAnswerStudentOptionsQryExe {

    private final StudentGateway studentGateway;

    public WrongAnswerStudentOptionsQryExe(StudentGateway studentGateway) {
        this.studentGateway = studentGateway;
    }

    public List<StudentOptionVO> execute() {
        return studentGateway.listAll().stream().map(WrongAnswerConverter::toOptionVO).toList();
    }
}
