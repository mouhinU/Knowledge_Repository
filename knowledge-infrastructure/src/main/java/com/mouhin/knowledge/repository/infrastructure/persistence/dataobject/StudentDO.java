package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 考生数据对象
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Getter
@Setter
@TableName("kb_student")
public class StudentDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("username")
    private String username;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("display_name")
    private String displayName;

    @TableField("student_no")
    private String studentNo;

    @TableField("department_id")
    private String departmentId;

    @TableField("session_token")
    private String sessionToken;

    @TableField("token_expiry")
    private LocalDateTime tokenExpiry;

    @TableField("status")
    private String status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
