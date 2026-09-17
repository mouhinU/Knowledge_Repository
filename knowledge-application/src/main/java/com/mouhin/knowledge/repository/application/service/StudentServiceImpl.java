package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.application.executor.student.StudentGetByIdQryExe;
import com.mouhin.knowledge.repository.application.executor.student.StudentListQryExe;
import com.mouhin.knowledge.repository.application.executor.student.StudentLoginCmdExe;
import com.mouhin.knowledge.repository.application.executor.student.StudentLogoutCmdExe;
import com.mouhin.knowledge.repository.application.executor.student.StudentRegisterCmdExe;
import com.mouhin.knowledge.repository.application.executor.student.StudentValidateTokenQryExe;
import com.mouhin.knowledge.repository.client.api.StudentServiceI;
import com.mouhin.knowledge.repository.client.dto.StudentLoginCmd;
import com.mouhin.knowledge.repository.client.dto.StudentRegisterCmd;
import com.mouhin.knowledge.repository.client.dto.StudentVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 考生认证应用服务实现（app 层，仅分发到 Executor）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Service
public class StudentServiceImpl implements StudentServiceI {

    private final StudentRegisterCmdExe studentRegisterCmdExe;
    private final StudentLoginCmdExe studentLoginCmdExe;
    private final StudentLogoutCmdExe studentLogoutCmdExe;
    private final StudentValidateTokenQryExe studentValidateTokenQryExe;
    private final StudentGetByIdQryExe studentGetByIdQryExe;
    private final StudentListQryExe studentListQryExe;

    public StudentServiceImpl(StudentRegisterCmdExe studentRegisterCmdExe,
                              StudentLoginCmdExe studentLoginCmdExe,
                              StudentLogoutCmdExe studentLogoutCmdExe,
                              StudentValidateTokenQryExe studentValidateTokenQryExe,
                              StudentGetByIdQryExe studentGetByIdQryExe,
                              StudentListQryExe studentListQryExe) {
        this.studentRegisterCmdExe = studentRegisterCmdExe;
        this.studentLoginCmdExe = studentLoginCmdExe;
        this.studentLogoutCmdExe = studentLogoutCmdExe;
        this.studentValidateTokenQryExe = studentValidateTokenQryExe;
        this.studentGetByIdQryExe = studentGetByIdQryExe;
        this.studentListQryExe = studentListQryExe;
    }

    @Override
    public StudentVO register(StudentRegisterCmd cmd) {
        return studentRegisterCmdExe.execute(cmd);
    }

    @Override
    public String login(StudentLoginCmd cmd) {
        return studentLoginCmdExe.execute(cmd);
    }

    @Override
    public void logout(String token) {
        studentLogoutCmdExe.execute(token);
    }

    @Override
    public Optional<StudentVO> validateToken(String token) {
        return studentValidateTokenQryExe.execute(token);
    }

    @Override
    public Optional<StudentVO> getStudentById(Long id) {
        return studentGetByIdQryExe.execute(id);
    }

    @Override
    public List<StudentVO> listStudents() {
        return studentListQryExe.execute();
    }
}
