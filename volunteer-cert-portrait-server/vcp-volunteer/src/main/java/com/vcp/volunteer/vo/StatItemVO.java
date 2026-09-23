package com.vcp.volunteer.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 组织端概览的单个指标项，形状与前端 mock 的 stats 数组一致：
 * key / label / value / unit / delta / trend。
 *
 * <p>delta 与 trend（环比）留空：后端没有历史快照表，算不出真实的环比，
 * 编一个数字反而会在答辩时被问住。前端 InkStat 的 delta 缺省为 null，
 * 此时不渲染环比那一行（分类管理页也是这么用的），因此留空是安全的。
 */
@Data
public class StatItemVO implements Serializable {

    /** 指标键，前端用于区分指标 */
    private String key;

    /** 指标名称 */
    private String label;

    /** 指标值 */
    private BigDecimal value;

    /** 单位 */
    private String unit;

    /** 环比变化（百分数），当前恒为空 */
    private BigDecimal delta;

    /** 趋势 up / down，当前恒为空 */
    private String trend;
}
