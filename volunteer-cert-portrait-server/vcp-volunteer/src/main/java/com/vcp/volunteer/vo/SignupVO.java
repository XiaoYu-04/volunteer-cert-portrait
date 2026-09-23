package com.vcp.volunteer.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 报名记录响应对象。
 *
 * <p>字段与前端「我的报名」「报名审核」两页的表格列对齐：
 * activityTitle / activityDate / activityHours / studentName / studentNo / college /
 * appliedAt / status / reason / rejectReason。
 *
 * <p><b>为什么要冗余活动与学生信息</b>：报名表本身只有 activity_id 与 student_id，
 * 而列表页要直接渲染活动名、活动日期、学生姓名与学号。这些字段由 SQL 侧一次 JOIN 取回，
 * 避免每行再查一次：前端一页 10 到 50 行，逐行查就是几十次数据库往返。
 *
 * <p>时间字段已在服务端格式化成 yyyy-MM-dd HH:mm:ss，前端直接渲染。
 */
@Data
public class SignupVO implements Serializable {

    private Long id;

    private Long activityId;

    /** 活动名称 */
    private String activityTitle;

    /** 活动日期 yyyy-MM-dd */
    private String activityDate;

    /** 活动预计时长（小时） */
    private BigDecimal activityHours;

    /** 学生档案 id（student_info.id） */
    private Long studentId;

    /** 学生姓名 */
    private String studentName;

    /** 学号 */
    private String studentNo;

    /** 学院 */
    private String college;

    /** 报名状态码 */
    private String status;

    /** 报名时间 yyyy-MM-dd HH:mm:ss */
    private String appliedAt;

    /** 报名理由 */
    private String reason;

    /** 驳回理由（审核备注），仅驳回时有值 */
    private String rejectReason;
}
