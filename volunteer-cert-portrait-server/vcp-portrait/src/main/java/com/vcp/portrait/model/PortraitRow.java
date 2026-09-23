package com.vcp.portrait.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 画像明细的联表查询投影（student_profile + student_info + sys_user）。
 *
 * <p>为什么用投影而不是实体：列表要按姓名/学院筛选、要显示学号，而这三项分别在
 * {@code sys_user.real_name}、{@code student_info.college} / {@code student_no}，
 * 画像表只有 student_id。逐条回查会 N+1，因此由
 * {@link com.vcp.portrait.mapper.PortraitAggregateMapper} 一条 JOIN 查询取齐。
 *
 * <p>字段名与 VO 不同：这里是「库里的原始值」（如 {@code level} 是中文等级名、
 * {@code tags} 是逗号分隔串），转换与补算在 Service 层完成。
 */
@Data
public class PortraitRow implements Serializable {

    /** 学生档案 id */
    private Long studentId;

    /** 姓名，取自 sys_user.real_name */
    private String studentName;

    /** 学号 */
    private String studentNo;

    private String college;

    private String major;

    /** 年级（入学年份），sql/06_backend_gap_fix2.sql 新增列 */
    private String grade;

    /** 公益等级，student_info.public_welfare_level 的原始值（中文等级名） */
    private String level;

    /** 已完成活动次数（画像快照） */
    private Integer totalActivities;

    /** 累计有效时长；取权威值 {@code student_info.total_duration}，不是画像快照那一列 */
    private BigDecimal totalDuration;

    /** 偏好活动类型 */
    private String categoryPreference;

    /** 公益标签，逗号分隔 */
    private String tags;

    /** 画像生成时间，取自 student_profile.update_time */
    private LocalDateTime generatedAt;
}
