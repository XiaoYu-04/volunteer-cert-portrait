package com.vcp.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户角色关联实体，对应 sys_user_role 表。
 *
 * <p>关联表按物理删除设计：没有 deleted，也没有 update_time（见 02_schema.sql 的表注释）。
 * 因此本类不继承 BaseEntity —— 基类带 updateTime 且标了 INSERT_UPDATE 填充，
 * 继承过来会让 INSERT 语句多出一个不存在的列而直接报错。
 *
 * <p>本系统一个账号只对应一个角色，但表结构允许一对多，代码按"取第一条"处理。
 */
@Data
@TableName("sys_user_role")
public class SysUserRole implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID，指向 sys_user.id */
    private Long userId;

    /** 角色ID，指向 sys_role.id */
    private Long roleId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
