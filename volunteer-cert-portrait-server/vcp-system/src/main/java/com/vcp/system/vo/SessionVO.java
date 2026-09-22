package com.vcp.system.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录会话对象：登录、注册、查询本人信息、修改本人资料四个接口都返回它。
 *
 * <p>字段与前端 stores/user.js 的 state.info 逐条对应，
 * 页面直接读 user.info.xxx，少一个字段就会在页面上显示成空。
 *
 * <p><b>刻意不含密码、手机号、邮箱</b>：会话对象会被存进 localStorage，
 * 只放页面渲染必需的字段。个人资料页要显示的联系方式由单独接口提供。
 */
@Data
public class SessionVO implements Serializable {

    /** 用户 id（sys_user.id） */
    private Long id;

    private String username;

    /** 真实姓名，取自 sys_user.real_name */
    private String name;

    /** 角色码：STUDENT / ORG_ADMIN / SCHOOL_ADMIN */
    private String role;

    /** 角色中文名，取自 sys_role.role_name */
    private String roleLabel;

    /** 所属志愿组织 id，仅组织管理员有值 */
    private Long orgId;

    /** 学生档案 id，仅学生有值 */
    private Long studentId;

    /** 学院，仅学生有值 */
    private String college;

    /** 学号，仅学生有值 */
    private String studentNo;

    /** 印章文字：姓名末两字，供前端头像印章直接使用 */
    private String avatarText;
}
