package com.vcp.portrait.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学生已完成活动的首末次时间投影，用于画像的「持续性」维度。
 *
 * <p>两条时间都取 {@code COALESCE(volunteer_activity.start_time, activity_signup.signup_time)}：
 * {@code start_time} 在库里可空（B17 记录的缺口），落空时退回报名时间，
 * 避免整条记录因为一个空列而从统计里消失。
 */
@Data
public class ActivitySpanRow implements Serializable {

    /**
     * 已完成活动次数，恒非空。
     *
     * <p>它的作用是<b>让本投影永远不会变成 null 元素</b>：只有首末次时间两列时，
     * 没有已完成活动的学生（两列都为 NULL）会让整行映射结果全为 null，MyBatis 会返回
     * {@code null} 而不是一个空对象（{@code returnInstanceForEmptyRow} 默认 false）。
     * 有了 {@code COUNT(*)}，调用方拿到的是「次数 0 + 两个 null 时间」，语义也更清楚。
     */
    private Integer completedCount;

    /** 首场已完成活动的开始时间；无已完成活动时为 null */
    private LocalDateTime firstTime;

    /** 最后一场已完成活动的开始时间；无已完成活动时为 null */
    private LocalDateTime lastTime;
}
