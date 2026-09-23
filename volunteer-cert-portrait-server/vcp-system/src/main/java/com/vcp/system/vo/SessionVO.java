package com.vcp.system.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录会话对象：登录、注册、查询本人信息、修改本人资料四个接口都返回它。
 *
 * <p>字段与前端 stores/user.js 的 state.info 逐条对应，
 * 页面直接读 user.info.xxx，少一个字段就会在页面上显示成空。
 *
 * <p><b>不含密码</b>；{@code phone} / {@code email} 由本类一并返回，
 * 供个人资料页回显 —— 此前刻意不含，导致学生端「手机号 / 邮箱」输入框永远空白，
 * 而学生没有任何别的接口能读到自己的联系方式（/v1/system/students/{id} 是学校管理员专属）。
 *
 * <p>更正一处历史注释：旧注释称「会话对象会被存进 localStorage，只放页面渲染必需的字段」，
 * 前提并不成立 —— 前端 stores/user.js 只把 **token** 写进 localStorage，
 * {@code info} 仅存在 Pinia 内存里，刷新后由路由守卫调 /auth/me 重新拉取。
 * 因此把联系方式放进本对象并不会把它落到浏览器存储里。
 * 这两个字段都只返回**调用者本人**的数据，不存在越权面。
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

    /** 手机号，取自 sys_user.phone，供个人资料页回显 */
    private String phone;

    /** 邮箱，取自 sys_user.email，供个人资料页回显 */
    private String email;

    /** 印章文字：姓名末两字，供前端头像印章直接使用 */
    private String avatarText;
}
