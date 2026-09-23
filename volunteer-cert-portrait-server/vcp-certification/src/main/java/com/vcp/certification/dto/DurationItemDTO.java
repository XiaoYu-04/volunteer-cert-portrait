package com.vcp.certification.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 单条服务时长提交项。
 *
 * <p>前端只保证给 {@code studentId} + {@code activityId}（其余字段是从候选名单里带出来的
 * 冗余信息），因此 <b>signup_id、活动名称、活动类型、所属组织一律由服务端反查</b>，
 * 请求里的 activityType / proof 只作兜底与附加信息。
 *
 * <p>{@code studentId} 是学生档案 id（student_info.id），不是 sys_user.id ——
 * 活动报名的 student_id 也是这个口径，两者必须一致，混用会反查不到报名记录。
 */
@Data
public class DurationItemDTO implements Serializable {

    /** 学生档案 id（student_info.id） */
    private Long studentId;

    /** 活动 id（volunteer_activity.id） */
    private Long activityId;

    /** 服务时长（小时），必须大于 0 */
    private BigDecimal hours;

    /** 证明材料路径/URL，可选 */
    private String proof;

    /** 活动类型（分类名），可选；活动已挂分类时以库里的分类名为准 */
    private String activityType;
}
