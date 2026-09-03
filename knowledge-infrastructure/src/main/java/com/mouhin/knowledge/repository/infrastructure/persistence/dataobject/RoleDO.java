package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 角色数据对象
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Getter
@Setter
@TableName("sys_role")
public class RoleDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("role_key")
    private String roleKey;

    @TableField("role_name")
    private String roleName;

    @TableField("description")
    private String description;

    @TableField("create_time")
    private LocalDateTime createTime;
}
