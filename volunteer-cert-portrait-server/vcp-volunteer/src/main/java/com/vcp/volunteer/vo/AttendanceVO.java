package com.vcp.volunteer.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 签到记录响应对象。
 *
 * <p>字段与前端「签到管理」页的表格列对齐：studentName / studentNo / college /
 * activityTitle / signInAt / signOutAt / hours / status。
 *
 * <p>「时长提交」页也用它：按 activityId + status=SIGNED_OUT 拉候选人名单，
 * 取的是 studentId / studentName / studentNo / college / signOutAt / hours 这几个字段，
 * 改动字段名会同时影响 vcp-certification 的提交入口。
 *
 * <p>status 与 hours 都是<b>惰性判定后</b>的结果：库里是 NOT_SIGNED 但已过签退窗口的记录，
 * 这里返回 ABSENT、时长 0；已签到未签退的返回 SIGNED_IN、时长按活动预计时长计
 * （见 com.vcp.volunteer.support.AttendancePolicy）。
 */
@Data
public class AttendanceVO implements Serializable {

    private Long id;

    /** 报名 id */
    private Long signupId;

    private Long activityId;

    /** 活动名称 */
    private String activityTitle;

    /** 学生档案 id */
    private Long studentId;

    /** 学生姓名 */
    private String studentName;

    /** 学号 */
    private String studentNo;

    /** 学院 */
    private String college;

    /** 签到状态码 */
    private String status;

    /** 签到时间 yyyy-MM-dd HH:mm:ss；未签到为空 */
    private String signInAt;

    /** 签退时间 yyyy-MM-dd HH:mm:ss；未签退为空 */
    private String signOutAt;

    /** 活动开始时间 yyyy-MM-dd HH:mm:ss；学生端用来显示签到窗口 */
    private String activityStartAt;

    /** 活动结束时间 yyyy-MM-dd HH:mm:ss */
    private String activityEndAt;

    /**
     * 当前是否可签到（服务端按待办 A2 的窗口口径算好）。
     *
     * <p>前端按钮显隐直接用这个字段，<b>不要在前端重复 30 分钟窗口常量</b>：
     * 两边各写一份，改一处就会漂成「按钮能点但接口必然拒绝」。
     */
    private Boolean canSignIn;

    /**
     * 当前是否可签退，口径同 {@link #canSignIn}。
     *
     * <p>已签到但签退窗口已关闭的记录（待办 A2 里保留 {@code SIGNED_IN} 的那批）这里是 false，
     * 需要组织管理员在「签到管理」里人工修正。
     */
    private Boolean canSignOut;

    /** 实得时长（小时），2 位小数 */
    private BigDecimal hours;
}
