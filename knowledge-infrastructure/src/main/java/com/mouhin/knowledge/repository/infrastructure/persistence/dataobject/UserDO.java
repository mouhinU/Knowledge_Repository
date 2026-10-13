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

    @TableField("tenant_id")
    private Long tenantId;

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

    /**
     * 首次登录是否必须改密（V28 加列，P3-T3 邀请式引导）。
     *
     * <p>{@code TINYINT(1) NOT NULL DEFAULT 0}；MyBatis-Plus 以 {@link Boolean} 映射 0/1。
     */
    @TableField("must_change_password")
    private Boolean mustChangePassword;

    @TableField("status")
    private String status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
