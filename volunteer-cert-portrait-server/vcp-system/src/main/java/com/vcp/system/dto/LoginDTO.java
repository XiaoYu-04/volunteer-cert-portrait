package com.vcp.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求体。
 *
 * <p>刻意不给 password 加长度校验：登录时密码规则不是重点，
 * 写死规则反而会把"老账号密码较短"的用户挡在门外，也容易被人拿来探测密码策略。
 * 真正的门槛在注册接口。
 */
@Data
public class LoginDTO implements Serializable {

    @NotBlank(message = "请输入用户名")
    private String username;

    @NotBlank(message = "请输入密码")
    private String password;
}
