package com.vcp.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求体。
 *
 * <p>字段名 {@code username} 保持不变，但语义是<b>「用户名或学号」</b>：
 * 前端与 mock 层都按这个名字提交，改成 account / studentNo 会让两侧契约同时断掉。
 * 由后端按「输入是否纯数字」识别后决定查哪张表（见 AuthServiceImpl#findByAccount），
 * 前端不需要、也不应该自己判断用户填的是哪一种。
 *
 * <p>刻意不给 password 加长度校验：登录时密码规则不是重点，
 * 写死规则反而会把"老账号密码较短"的用户挡在门外，也容易被人拿来探测密码策略。
 * 真正的门槛在注册接口。
 */
@Data
public class LoginDTO implements Serializable {

    /**
     * 用户名或学号。
     *
     * <p>这里不做格式校验：纯数字既可能是学号、也可能是用户名（用户名规则允许 1234 这类），
     * 只有查到库才知道该按哪种解释，注解层面判不出对错。
     */
    @NotBlank(message = "请输入用户名")
    private String username;

    @NotBlank(message = "请输入密码")
    private String password;
}
