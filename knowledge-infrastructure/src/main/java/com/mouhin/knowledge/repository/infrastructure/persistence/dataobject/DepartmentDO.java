package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 部门数据对象
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Getter
@Setter
@TableName("sys_department")
public class DepartmentDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("department_key")
    private String departmentKey;

    @TableField("department_name")
    private String departmentName;

    @TableField("parent_id")
    private Long parentId;

    @TableField("create_time")
    private LocalDateTime createTime;
}
