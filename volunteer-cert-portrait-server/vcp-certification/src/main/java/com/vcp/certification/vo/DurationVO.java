package com.vcp.certification.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 服务时长响应对象，字段名与前端契约（api/certification.js + mock/data/certification.js）逐字对齐。
 *
 * <p>注意两点：
 * <ul>
 *   <li><b>hours</b> 对应库里的 service_duration.duration，前端列名就叫 hours，
 *       不要改成 duration，否则列表里的时长列会全部空白。</li>
 *   <li><b>时间字段是字符串</b>：前端把 submittedAt / auditedAt 直接交给 formatDateTime 渲染，
 *       返回 LocalDateTime 会带上 ISO 的 T。格式化在 SQL 里用 to_char 完成
 *       （见 {@code DurationRefMapper}），格式与 DateTimeUtils 一致。</li>
 * </ul>
 *
 * <p>学生姓名、学号、学院、活动名称与类型、组织名称、审核人姓名都不在 service_duration 表上，
 * 由查询时 JOIN 拼出（同上）。
 */
@Data
public class DurationVO implements Serializable {

    private Long id;

    /** 学生档案 id（student_info.id） */
    private Long studentId;

    /** 学生姓名（来自 sys_user.real_name） */
    private String studentName;

    private String studentNo;

    private String college;

    private Long activityId;

    private String activityTitle;

    /** 活动类型（分类名），用于画像标签统计 */
    private String activityType;

    private Long orgId;

    /** 提交组织名称 */
    private String orgName;

    /** 服务时长（小时），对应库里的 duration */
    private BigDecimal hours;

    /** 服务日期 yyyy-MM-dd，取活动开始时间 */
    private String serviceDate;

    /** 状态英文码：PENDING_SUBMIT / PENDING_AUDIT / APPROVED / REJECTED */
    private String status;

    /** 提交时间 yyyy-MM-dd HH:mm:ss；未提交时为空 */
    private String submittedAt;

    /** 审核时间 yyyy-MM-dd HH:mm:ss；未审核时为空 */
    private String auditedAt;

    /** 审核人姓名；未审核时为空 */
    private String auditor;

    /** 审核意见（驳回原因） */
    private String remark;

    /** 证明材料路径/URL */
    private String proof;
}
