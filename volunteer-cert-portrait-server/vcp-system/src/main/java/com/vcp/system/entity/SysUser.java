package com.vcp.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vcp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户实体，对应 sys_user 表。
 *
 * <p><b>status 的存法</b>：库里是 SMALLINT（1 启用 / 0 停用），而前端用的是
 * ACTIVE / DISABLED 字符串。转换统一由 UserStatusEnum 承担，
 * 实体这里保持数据库原样，不要在图省事的地方直接拼字符串。
 *
 * <p><b>password 当前是明文</b>（待办 B15）。本类不覆盖 toString，Lombok 生成的
 * toString() 会把密码一起打出来，因此禁止把本对象直接塞进日志；
 * 对外一律用 UserVO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号，唯一 */
    private String username;

    /** 密码，当前为明文（B15 接入加密后替换） */
    private String password;

    /** 真实姓名，前端字段名 name */
    private String realName;

    private String phone;

    private String email;

    /** 头像地址 */
    private String avatar;

    /** 状态：1 启用 / 0 停用，见 UserStatusEnum */
    private Integer status;

    /** 最近一次登录成功时间，用户管理页展示用 */
    private LocalDateTime lastLoginAt;

    /** 逻辑删除：0 未删除 / 1 已删除 */
    @TableLogic
    private Integer deleted;
}
