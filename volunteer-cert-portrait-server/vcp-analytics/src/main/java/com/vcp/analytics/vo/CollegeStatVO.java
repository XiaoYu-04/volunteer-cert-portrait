package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 学院志愿时长统计，对应前端 {@code charts.college} 与 {@code RankList}。
 *
 * <p>字段与 {@code dataset.js} 的 {@code colleges} 对齐：{@code {college, hours, students, avg}}。
 * 前端按 hours 降序取前 8 名展示，排名条的宽度也以首行为基准，
 * 因此本列表<b>必须按 hours 降序返回</b>，否则"第一名"会取错基准。
 *
 * <p>hours 以 {@code student_info.total_duration} 为准（已拍板口径，见 A5），
 * 不取 {@code student_profile.total_duration} —— 后者只是画像快照。
 */
@Data
public class CollegeStatVO implements Serializable {

    /** 学院名称 */
    private String college;

    /** 该学院累计认证志愿时长（小时） */
    private BigDecimal hours;

    /** 该学院学生人数 */
    private Long students;

    /** 人均时长（小时） */
    private BigDecimal avg;
}
