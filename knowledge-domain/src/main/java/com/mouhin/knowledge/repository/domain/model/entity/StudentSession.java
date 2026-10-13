package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 考生会话实体（P0-T5，多会话支持）
 *
 * <p>每次成功登录落一行，独立于 {@link Student#getSessionToken()} 的「最后 token」快照， 允许多端并发登录而不互踢。业务侧凭 {@code
 * sessionToken} 反查会话即视为该考生在线。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
public class StudentSession {

    private Long id;

    /** 会话令牌：UUID 去横线（32 位十六进制） */
    private String sessionToken;

    /** 关联考生主键 */
    private Long studentId;

    /** 过期时间：登录时刻 + 令牌 TTL */
    private LocalDateTime expiresAt;

    /** 撤销时间；{@code null} = 未撤销 */
    private LocalDateTime revokedAt;

    /** 登录时 User-Agent（可选，用于审计） */
    private String userAgent;

    /** 登录时 IP（可选，用于审计） */
    private String ipAddress;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== Getters & Setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    // ==================== 业务方法 ====================

    /** 会话是否有效：未撤销且未过期。 */
    public boolean isActive() {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(LocalDateTime.now());
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
        StudentSession that = (StudentSession) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
