package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 活动与服务时长趋势的一个月份点，对应前端 {@code charts.trend} / {@code charts.hours}。
 *
 * <p>字段与 {@code dataset.js} 的 {@code trend} 对齐：{@code {month, count, hours}}。
 *
 * <p><b>month 必须是 "YYYY-MM" 文本</b>：图表把 x 轴标签写成
 * {@code `${Number(month.slice(5))}月`}，直接对字符串按下标切片，
 * 换成 ISO 时间串（"2025-03-01T00:00:00"）会切出 "01T00:00:00"，图就废了。
 *
 * <p>count 与 hours 都按<b>活动开始时间</b>归属月份：一张图同时看"活动量"与"服务量"，
 * 两者必须同一时间基准，否则两条曲线会对不上。没有数据的月份返回 0 而不是缺行 ——
 * 折线图缺行会直接把相邻两点连起来，看起来像那几个月没有间断。
 */
@Data
public class TrendPointVO implements Serializable {

    /** 月份，格式 yyyy-MM */
    private String month;

    /** 当月活动场次 */
    private Long count;

    /** 当月已通过认证的服务时长（小时） */
    private BigDecimal hours;
}
