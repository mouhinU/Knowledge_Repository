package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * 考生实体
 *
 * @author mouhinU
 * @date 2026-09-15
 */
@Getter
@Setter
public class Student {

    private Long id;

    /** 登录用户名 */
    private String username;

    /** 密码哈希（BCrypt） */
    private String passwordHash;

    /** 显示名称 */
    private String displayName;

    /** 学号 / 工号 */
    private String studentNo;

    /** 部门 ID */
    private String departmentId;

    /** 会话令牌 */
    private String sessionToken;

    /** 令牌过期时间 */
    private LocalDateTime tokenExpiry;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 业务方法 ====================

    /** 判断令牌是否有效 */
    public boolean isTokenValid() {
        return sessionToken != null
                && tokenExpiry != null
                && tokenExpiry.isAfter(LocalDateTime.now());
    }

    // ==================== equals / hashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Student student = (Student) o;
        return Objects.equals(id, student.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
