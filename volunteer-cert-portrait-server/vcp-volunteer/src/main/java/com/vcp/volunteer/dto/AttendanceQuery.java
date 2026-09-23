package com.vcp.volunteer.dto;

import com.vcp.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 签到记录列表查询条件（GET /api/v1/attendance）。
 *
 * <p>只有组织管理员与学校管理员能访问（权限 {@code volunteer:attendance:manage}）。
 * 组织管理员的数据范围由登录态里的 orgId 限定，前端传的 orgId 一律忽略。
 *
 * <p>status 与展示状态一致：库里是 NOT_SIGNED 但已过签退窗口的记录，
 * 按 ABSENT 也能筛出来（惰性判定，见 AttendancePolicy 与对应 XML 的说明）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AttendanceQuery extends PageQuery {

    /** 关键字，匹配学生姓名、学号或活动名称 */
    private String keyword;

    /** 活动 id */
    private Long activityId;

    /** 学生档案 id */
    private Long studentId;

    /** 签到状态码，全等匹配 */
    private String status;

    /** 组织 id；组织管理员下该参数被登录态覆盖 */
    private Long orgId;
}
