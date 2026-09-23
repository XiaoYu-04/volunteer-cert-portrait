package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 看板首页的"本期志愿活动"卡片，对应前端 {@code ActivityCard} 组件。
 *
 * <p>字段与 {@code dataset.js} 的 {@code activities} 中该组件实际读取的字段对齐：
 * {@code {id, title, type, date, time, place, enrolled, capacity, org}}。
 *
 * <p><b>date 与 time 是分开的两个字符串</b>：卡片把时间渲染成
 * {@code `${a.date} ${a.time}`}（如 "2025-03-22 09:00"），不是日期时间一体。
 * 两列都由服务端格式化，避免前端拿到 ISO 串后出现 {@code T}。
 *
 * <p>enrolled / capacity 对应 {@code volunteer_activity.signed_count} 与 {@code max_count}，
 * 卡片用它们画报名进度条；max_count 为 0 表示不限名额，此时进度条按 0% 处理
 * （前端的 {@code if (!capacity) return 0}），不需要后端特殊处理。
 */
@Data
public class DashboardActivityVO implements Serializable {

    private Long id;

    private String title;

    /** 活动分类名 */
    private String type;

    /** 活动日期，格式 yyyy-MM-dd */
    private String date;

    /** 活动开始时刻，格式 HH:mm */
    private String time;

    private String place;

    /** 已报名人数 */
    private Integer enrolled;

    /** 人数上限，0 表示不限 */
    private Integer capacity;

    /** 发布组织名称 */
    private String org;
}
