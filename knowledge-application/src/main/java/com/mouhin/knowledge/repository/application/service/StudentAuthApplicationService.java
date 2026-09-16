package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.domain.model.entity.Student;
import com.mouhin.knowledge.repository.domain.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 考生认证应用服务
 * <p>
 * 提供考生注册、登录、令牌验证等功能。
 * 使用 BCrypt 哈希存储密码，UUID 令牌实现会话管理。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Service
public class StudentAuthApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(StudentAuthApplicationService.class);

    /**
     * 令牌有效期：24 小时
     */
    private static final int TOKEN_VALIDITY_HOURS = 24;

    private final StudentRepository studentRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public StudentAuthApplicationService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 考生注册
     *
     * @param username    用户名
     * @param password    明文密码
     * @param displayName 显示名称（可为 null）
     * @param studentNo   学号 / 工号（可为 null）
     * @return 注册成功的考生（不含密码）
     */
    @Transactional
    public Student register(String username, String password, String displayName, String studentNo) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度至少 6 位");
        }

        Optional<Student> existing = studentRepository.findByUsername(username);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }

        Student student = new Student();
        student.setUsername(username);
        student.setPasswordHash(passwordEncoder.encode(password));
        student.setDisplayName(displayName != null ? displayName : username);
        student.setStudentNo(studentNo);
        student.setStatus("ACTIVE");
        student.setCreateTime(LocalDateTime.now());
        student.setUpdateTime(LocalDateTime.now());

        studentRepository.save(student);
        logger.info("考生注册成功: username='{}', id={}", username, student.getId());
        return student;
    }

    /**
     * 考生登录
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 会话令牌
     */
    @Transactional
    public String login(String username, String password) {
        Student student = studentRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        if (!"ACTIVE".equals(student.getStatus())) {
            throw new IllegalArgumentException("账号已被禁用");
        }

        if (!passwordEncoder.matches(password, student.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        student.setSessionToken(token);
        student.setTokenExpiry(LocalDateTime.now().plusHours(TOKEN_VALIDITY_HOURS));
        student.setUpdateTime(LocalDateTime.now());
        studentRepository.update(student);

        logger.info("考生登录成功: username='{}', id={}", username, student.getId());
        return token;
    }

    /**
     * 根据令牌验证考生身份
     *
     * @param token 会话令牌
     * @return 考生实体，无效则返回空
     */
    public Optional<Student> validateToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<Student> opt = studentRepository.findBySessionToken(token);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        Student student = opt.get();
        if (!student.isTokenValid()) {
            return Optional.empty();
        }
        if (!"ACTIVE".equals(student.getStatus())) {
            return Optional.empty();
        }
        return Optional.of(student);
    }

    /**
     * 退出登录
     */
    @Transactional
    public void logout(String token) {
        studentRepository.findBySessionToken(token).ifPresent(student -> {
            student.setSessionToken(null);
            student.setTokenExpiry(null);
            student.setUpdateTime(LocalDateTime.now());
            studentRepository.update(student);
            logger.info("考生退出登录: id={}", student.getId());
        });
    }

    /**
     * 查询所有考生
     */
    public List<Student> listStudents() {
        return studentRepository.listAll();
    }

    /**
     * 根据 ID 查询考生
     */
    public Optional<Student> getStudentById(Long id) {
        return studentRepository.findById(id);
    }
}
