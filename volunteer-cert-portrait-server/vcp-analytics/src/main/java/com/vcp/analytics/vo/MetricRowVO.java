package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 核心指标的聚合结果行，仅用于承载 Mapper 的查询结果，不直接对外返回。
 *
 * <p>一行 = 一个指标的本期值与基期值，两列都是 SQL 里聚合好的数字，
 * 环比由服务层用这两个数算出（不涉及任何明细数据）。
 * 拆成"键 + 两个值"而不是六个具名属性，是为了让指标清单只有一处定义：
 * 加一个指标只需在 SQL 里加一段 UNION ALL、在服务层加一行映射。
 */
@Data
public class MetricRowVO implements Serializable {

    /** 指标键：activities / enrolled / hours / monthNew / signRate / passRate */
    private String metricKey;

    /** 本期值 */
    private BigDecimal metricValue;

    /** 基期（上月末或上月）值；无法比较时为 null */
    private BigDecimal prevValue;
}
