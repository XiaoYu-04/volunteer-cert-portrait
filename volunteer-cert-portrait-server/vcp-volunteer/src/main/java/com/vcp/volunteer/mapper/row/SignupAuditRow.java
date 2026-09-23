package com.vcp.volunteer.mapper.row;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 审核一条报名所需要的全部上下文，一次查回。
 *
 * <p>审核通过要给签到记录建行、要给学生发通知，因此除了报名自身状态，
 * 还需要：学生档案 id 与学生账号 id（通知按 sys_user.id 落行，
 * 而报名表存的是 student_info.id）、活动名称与所属组织（通知正文与数据范围校验）、
 * 活动预计时长（签到记录里不需要，留给后续时长提交兜底）。
 *
 * <p>逐条查询要四次往返，这里合成一条 SQL：审核是写操作，
 * 多几次往返不影响体验，但把取数与判断分开写更不容易漏字段。
 */
@Data
public class SignupAuditRow implements Serializable {

    private Long signupId;

    private String status;

    private Long studentId;

    /** 学生账号 id（student_info.user_id），通知的收件人 */
    private Long studentUserId;

    private String studentName;

    private Long activityId;

    private String activityTitle;

    private Long activityOrgId;

    private String activityOrgName;

    private BigDecimal activityDuration;
}
