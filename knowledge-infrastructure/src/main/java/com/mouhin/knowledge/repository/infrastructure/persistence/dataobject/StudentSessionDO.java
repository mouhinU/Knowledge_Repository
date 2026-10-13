package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 考生会话数据对象（P0-T5，多会话支持）
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@Getter
@Setter
@TableName("kb_student_session")
public class StudentSessionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("session_token")
    private String sessionToken;

    @TableField("student_id")
    private Long studentId;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("revoked_at")
    private LocalDateTime revokedAt;

    @TableField("user_agent")
    private String userAgent;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
