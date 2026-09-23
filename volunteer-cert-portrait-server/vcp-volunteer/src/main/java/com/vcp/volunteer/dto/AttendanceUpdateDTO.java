package com.vcp.volunteer.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 签到记录人工修正请求（PUT /api/v1/attendance/{id}）。
 *
 * <p>现场设备异常导致漏签时由组织管理员修正。三个字段都是「覆盖式」提交：
 * 前端把空串当作「无该时间」提交（置为未签到时两个时间都为空串），
 * 因此空串一律解析成 null，而不是保留原值。
 */
@Data
public class AttendanceUpdateDTO implements Serializable {

    /** 目标状态：NOT_SIGNED / SIGNED_IN / SIGNED_OUT / ABNORMAL / ABSENT */
    private String status;

    /** 签到时间，格式 2025-03-22 08:45 或 2025-03-22 08:45:00；空串表示未签到 */
    private String signInAt;

    /** 签退时间；状态置为 SIGNED_OUT 时必填，空串表示未签退 */
    private String signOutAt;
}
