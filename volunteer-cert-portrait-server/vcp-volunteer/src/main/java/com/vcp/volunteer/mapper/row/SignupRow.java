package com.vcp.volunteer.mapper.row;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报名列表查询结果，含 JOIN 出来的活动与学生信息。
 *
 * <p>studentName 来自 sys_user.real_name（姓名不在 student_info 上），
 * studentNo / college 来自 student_info。
 */
@Data
public class SignupRow implements Serializable {

    private Long id;

    private Long activityId;

    private String activityTitle;

    private LocalDateTime activityStartTime;

    private BigDecimal activityDuration;

    private Long studentId;

    private String studentName;

    private String studentNo;

    private String college;

    private String status;

    private LocalDateTime signupTime;

    private String reason;

    private String auditRemark;
}
