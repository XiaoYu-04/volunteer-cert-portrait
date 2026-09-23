package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 单月签到热力日历，对应前端 {@code charts.heat}。
 *
 * <p>字段与 {@code dataset.js} 的 {@code heatmap} 对齐：{@code {month, days}}。
 *
 * <p><b>days 必须逐日齐全且按日序排列</b>：{@code heatmapSeries} 用下标反推日期
 * （{@code `${month}-${String(i + 1).padStart(2, '0')}`}），少一天就会让后面所有日子
 * 整体错位一天，而且不会报错。因此服务层按当月实际天数（28/30/31）返回，
 * 没有签到记录的日子补 0。
 *
 * <p>month 取<b>最近一个有签到记录的月份</b>：日历控件是按月渲染的，若固定取当前月，
 * 月初打开看板会是一整片空白（当月还没有签到数据），看起来像接口坏了。
 * 库里完全没有签到记录时才回退到当前月，此时 days 全为 0。
 */
@Data
public class HeatmapVO implements Serializable {

    /** 月份，格式 yyyy-MM */
    private String month;

    /** 当月逐日签到人次，下标 0 对应 1 日 */
    private List<Integer> days;
}
