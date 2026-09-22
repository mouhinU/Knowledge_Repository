package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户数据对象
 *
 * @author mouhinU
 * @date 2026-09-02
 */
@Getter
@Setter
@TableName("sys_user")
public class UserDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_key")
    private String userKey;

    @TableField("username")
    private String username;

    @TableField("department_id")
    private String departmentId;

    @TableField("is_admin")
    private Boolean admin;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("status")
    private String status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
