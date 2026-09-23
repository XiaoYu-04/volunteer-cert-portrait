package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 签到热力日历的逐日聚合结果行，仅用于承载 Mapper 的查询结果，不直接对外返回。
 *
 * <p>查询按 day 升序返回当月每一天（无记录的日子补 0），服务层据此顺序拼成
 * {@link HeatmapVO#getDays()}。day 保留在结果里，是为了让"行序 = 日序"这件事可核对：
 * 一旦有人改了排序，从 day 列就能看出错位，不必等到热力图整体偏移一天才发现。
 */
@Data
public class HeatmapDayVO implements Serializable {

    /** 月份，格式 yyyy-MM；同一次查询的所有行相同 */
    private String month;

    /** 当月第几天，1~31 */
    private Integer day;

    /** 当日签到人次 */
    private Integer count;
}
