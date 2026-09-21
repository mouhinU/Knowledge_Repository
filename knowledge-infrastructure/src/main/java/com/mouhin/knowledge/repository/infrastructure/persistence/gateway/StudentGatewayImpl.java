package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.StudentConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.StudentDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.StudentMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 考生仓储实现
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Repository
public class StudentGatewayImpl implements StudentGateway {

    private final StudentMapper studentMapper;

    public StudentGatewayImpl(StudentMapper studentMapper) {
        this.studentMapper = studentMapper;
    }

    @Override
    public void save(Student student) {
        StudentDO doObj = StudentConverter.toDO(student);
        studentMapper.insert(doObj);
        student.setId(doObj.getId());
    }

    @Override
    public void update(Student student) {
        StudentDO doObj = StudentConverter.toDO(student);
        studentMapper.updateById(doObj);
    }

    @Override
    public void clearSessionToken(Long id) {
        LambdaUpdateWrapper<StudentDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(StudentDO::getId, id)
                .set(StudentDO::getSessionToken, null)
                .set(StudentDO::getTokenExpiry, null)
                .set(StudentDO::getUpdateTime, LocalDateTime.now());
        studentMapper.update(null, wrapper);
    }

    @Override
    public Optional<Student> findById(Long id) {
        StudentDO doObj = studentMapper.selectById(id);
        return Optional.ofNullable(StudentConverter.toDomain(doObj));
    }

    @Override
    public Optional<Student> findByUsername(String username) {
        LambdaQueryWrapper<StudentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentDO::getUsername, username);
        StudentDO doObj = studentMapper.selectOne(wrapper);
        return Optional.ofNullable(StudentConverter.toDomain(doObj));
    }

    @Override
    public Optional<Student> findBySessionToken(String token) {
        LambdaQueryWrapper<StudentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentDO::getSessionToken, token);
        StudentDO doObj = studentMapper.selectOne(wrapper);
        return Optional.ofNullable(StudentConverter.toDomain(doObj));
    }

    @Override
    public List<Student> listAll() {
        LambdaQueryWrapper<StudentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(StudentDO::getCreateTime);
        return studentMapper.selectList(wrapper).stream().map(StudentConverter::toDomain).toList();
    }

    @Override
    public List<Student> listByDepartment(String departmentId) {
        LambdaQueryWrapper<StudentDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudentDO::getDepartmentId, departmentId).orderByDesc(StudentDO::getCreateTime);
        return studentMapper.selectList(wrapper).stream().map(StudentConverter::toDomain).toList();
    }

    @Override
    public void deleteById(Long id) {
        studentMapper.deleteById(id);
    }
}
