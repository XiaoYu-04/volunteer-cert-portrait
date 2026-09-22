package com.vcp.system.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户列表 / 详情响应对象。
 *
 * <p>字段与用户管理页的表格列一一对应（见 src/views/admin/UserManageView.vue 的 columns）：
 * username / name / role / phone / email / status / lastLoginAt。
 *
 * <p><b>没有 password 字段</b>：实体 SysUser 带着明文密码，任何情况下都不许直接
 * 返回给前端，必须经本类转换。这是本项目最容易犯的一类错误
 * （前端 mock 里就专门留了注释提醒：剔除密码要用重命名解构写法）。
 *
 * <p>role 返回的是<b>角色码</b>（如 STUDENT）而不是中文名：
 * 页面上先用 roleName(code) 去角色列表里查中文，筛选条件用的也是码。
 */
@Data
public class UserVO implements Serializable {

    private Long id;

    private String username;

    /** 真实姓名 */
    private String name;

    /** 角色码 */
    private String role;

    /** 角色中文名 */
    private String roleLabel;

    private String phone;

    private String email;

    /** 账号状态，前端口径 ACTIVE / DISABLED */
    private String status;

    /** 最近登录时间，已格式化为 yyyy-MM-dd HH:mm:ss */
    private String lastLoginAt;
}
