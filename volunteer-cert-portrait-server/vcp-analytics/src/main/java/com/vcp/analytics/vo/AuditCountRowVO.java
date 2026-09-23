package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 时长审核三态的聚合结果行，仅用于承载 Mapper 的查询结果，不直接对外返回。
 *
 * <p>三个计数由 SQL 一次算出：同一条记录不可能同时属于两个状态，故三者相加等于 total。
 * 服务层再按"已通过 → 待审核 → 已驳回"的固定顺序组装成 {@link AuditVO}。
 */
@Data
public class AuditCountRowVO implements Serializable {

    /** 已提交的记录总条数 */
    private Long total;

    /** 已通过条数 */
    private Long approved;

    /** 待审核条数 */
    private Long pending;

    /** 已驳回条数 */
    private Long rejected;

    /** 审核通过率，0~1 小数 */
    private BigDecimal passRate;
}
