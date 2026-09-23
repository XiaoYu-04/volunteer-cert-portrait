package com.vcp.org.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 组织资料更新请求。
 *
 * <p>状态、负责人账号与审核备注不在其中：它们分别走审核、账号管理和审核接口，
 * 混在资料更新里会绕开状态机。
 */
@Data
public class OrgUpdateDTO implements Serializable {

    @NotBlank(message = "请输入组织名称")
    private String name;

    private String code;

    private String orgType;

    private String contact;

    private String phone;

    private String email;

    private String college;

    @Min(value = 0, message = "成员人数不能小于 0")
    private Integer memberCount;

    private LocalDate foundedAt;

    private String intro;
}
