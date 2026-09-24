package com.vcp.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 学生自助注册请求体。
 *
 * <p>格式校验（用户名规则、手机号、邮箱）放在 Service 里用正则做，
 * 而不是写在这里的注解上：前端 mock 层已经定死了每条校验的提示文案
 * （如"用户名为 4-20 位字母、数字或下划线"），注解的 message 拼装不出同样的措辞，
 * 放 Service 里能保证两边提示一字不差。
 *
 * <p>注册只能产生学生账号，因此请求体里没有 role 字段 ——
 * 若允许前端传角色，任何人都能注册出学校管理员。
 */
@Data
public class RegisterDTO implements Serializable {

    @NotBlank(message = "请输入用户名")
    private String username;

    @NotBlank(message = "请输入姓名")
    private String name;

    @NotBlank(message = "请输入密码")
    private String password;

    private String phone;

    private String email;

    /**
     * 学院，取值必须是 sys_dict 里 dict_type = 'college' 的字典项。
     *
     * <p>这里连 {@code @NotBlank} 都不加：非空判定要先查字典，注解表达不了这种规则，
     * 提示文案也统一由 Service 产出（见类注释）。
     */
    private String college;
}
