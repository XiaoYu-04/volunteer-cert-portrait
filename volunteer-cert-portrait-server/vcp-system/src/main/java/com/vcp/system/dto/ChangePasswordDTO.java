package com.vcp.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 本人修改密码请求体（PUT /api/v1/auth/password）。
 *
 * <p>没有 userId 字段：改的永远是当前登录账号，userId 从登录态取。
 * 留一个 userId 参数等于给"改别人密码"开了口子 ——
 * 管理员给他人重置口令走用户管理接口，与这里不是一回事。
 *
 * <p>两个字段只做非空校验，长度等规则交给 {@code PasswordUtils.checkPolicy}
 * 在 Service 里判：那里的文案与注册、管理员新增/重置共用一套，
 * 写死在这里的注解上迟早会和别处对不齐（前端 mock 层已定死提示措辞）。
 */
@Data
public class ChangePasswordDTO implements Serializable {

    @NotBlank(message = "请输入原密码")
    private String oldPassword;

    @NotBlank(message = "请输入新密码")
    private String newPassword;
}
