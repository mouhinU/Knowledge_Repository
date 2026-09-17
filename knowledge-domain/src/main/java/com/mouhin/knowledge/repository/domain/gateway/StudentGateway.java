package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.Student;

import java.util.List;
import java.util.Optional;

/**
 * 考生仓储接口
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
public interface StudentGateway {

    void save(Student student);

    void update(Student student);

    Optional<Student> findById(Long id);

    Optional<Student> findByUsername(String username);

    Optional<Student> findBySessionToken(String token);

    List<Student> listAll();

    List<Student> listByDepartment(String departmentId);

    void deleteById(Long id);
}
