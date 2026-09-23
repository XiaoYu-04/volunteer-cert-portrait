package com.vcp.volunteer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 学生提交报名请求（POST /api/v1/signups）。
 *
 * <p><b>studentId 只用于兼容前端请求体，服务端一律忽略</b>：报名人身份取自登录会话，
 * 否则任何人改一个 id 就能替别人报名（见待办 B19 的越权风险）。
 */
@Data
public class SignupCreateDTO implements Serializable {

    /** 活动 id */
    @NotNull(message = "请选择要报名的活动")
    private Long activityId;

    /** 学生档案 id，服务端忽略，以登录态为准 */
    private Long studentId;

    /** 报名理由，可为空 */
    private String reason;
}
