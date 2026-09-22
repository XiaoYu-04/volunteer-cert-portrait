package com.vcp.system.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 修改本人资料请求体（PUT /api/v1/auth/me）。
 *
 * <p>只能改姓名、手机号、邮箱三项。用户名是登录凭据、角色决定权限、
 * 状态由管理员控制，都不在可自助修改的范围内。
 */
@Data
public class ProfileUpdateDTO implements Serializable {

    private String name;

    private String phone;

    private String email;
}
