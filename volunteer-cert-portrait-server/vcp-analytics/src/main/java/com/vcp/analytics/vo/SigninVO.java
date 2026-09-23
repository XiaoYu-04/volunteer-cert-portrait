package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 签到率统计，对应前端 {@code charts.sign} 仪表盘与看板的三个数字。
 *
 * <p>字段与 {@code dataset.js} 的 {@code signin} 对齐：{@code {rate, signed, total, absent}}。
 *
 * <p><b>rate 是 0~1 小数</b>：{@code charts.sign} 内部做 {@code (rate * 100).toFixed(1)}
 * 再喂给仪表盘，传百分数会显示成 "9230.0%"。
 *
 * <p>口径：total 是应签到人次（{@code attendance_record} 的行数，一条报名对应一行），
 * signed 是实际签到人次（{@code sign_in_time} 非空），absent 是缺勤人次
 * （状态 ABSENT）。用签到时间而不是状态判"实到"，是因为状态码里的 SIGNED_IN 只代表
 * "签到后还没签退"，拿它当实到数会把已签退的人全漏掉。
 */
@Data
public class SigninVO implements Serializable {

    /** 签到率，0~1 小数 */
    private BigDecimal rate;

    /** 实签到人次 */
    private Long signed;

    /** 应签到人次 */
    private Long total;

    /** 缺勤人次 */
    private Long absent;
}
