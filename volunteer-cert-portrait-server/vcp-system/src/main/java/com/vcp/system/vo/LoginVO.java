package com.vcp.system.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录 / 注册的响应体：{@code { token, user }}。
 *
 * <p>前端 stores/user.js 的 applySession 一次读走这两个字段：
 * token 写 localStorage，user 写 state.info。缺任何一个页面都会立刻出问题
 * （没 token 路由守卫会踢回登录页，没 user 则顶部导航姓名与角色为空）。
 */
@Data
public class LoginVO implements Serializable {

    /** 访问令牌，前端以 Authorization: Bearer <token> 携带 */
    private String token;

    /** 登录用户信息 */
    private SessionVO user;
}
