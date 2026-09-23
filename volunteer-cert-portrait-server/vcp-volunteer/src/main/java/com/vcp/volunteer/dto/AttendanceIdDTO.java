package com.vcp.volunteer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 签到 / 签退请求（POST /api/v1/attendance/sign-in、/sign-out）。
 *
 * <p>前端只传签到记录 id，学生身份取自登录会话，签到记录归属与时间窗口在 Service 里校验。
 */
@Data
public class AttendanceIdDTO implements Serializable {

    /** 签到记录 id */
    @NotNull(message = "签到记录不能为空")
    private Long attendanceId;
}
