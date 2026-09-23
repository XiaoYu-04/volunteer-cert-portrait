package com.vcp.volunteer.mapper.row;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 签到记录查询结果，含 JOIN 出来的报名、活动与学生信息。
 *
 * <p>activityEndTime 与 activityDuration 是惰性判定与时长计算的输入：
 * 前者决定「未签到是否已构成缺勤」，后者是缺签退时的兜底时长与 1.5 倍封顶的基数。
 */
@Data
public class AttendanceRow implements Serializable {

    private Long id;

    private Long signupId;

    private Long activityId;

    private String activityTitle;

    private LocalDateTime activityStartTime;

    private LocalDateTime activityEndTime;

    private BigDecimal activityDuration;

    /** 活动所属组织 id；签到记录本身不带 org_id，组织管理员的数据范围只能经活动反查 */
    private Long activityOrgId;

    private Long studentId;

    private String studentName;

    private String studentNo;

    private String college;

    private String status;

    private LocalDateTime signInTime;

    private LocalDateTime signOutTime;
}
