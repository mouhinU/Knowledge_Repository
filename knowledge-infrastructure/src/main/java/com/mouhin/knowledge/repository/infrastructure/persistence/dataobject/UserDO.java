package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户数据对象
 *
 * @author Knowledge-Repository
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

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
