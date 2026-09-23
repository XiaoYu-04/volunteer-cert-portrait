package com.vcp.certification.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 时长审核统计（看板与审核页顶部指标卡用）。
 *
 * <p><b>items 的顺序是契约</b>：前端按下标取值 —— {@code items[0]} 取「已通过」、
 * {@code items[2]} 取「已驳回」（用于算驳回率），顺序颠倒不会报错、只会让页面上的数字
 * 张冠李戴。顺序固定为：已通过 / 待审核 / 已驳回。
 *
 * <p><b>total 只统计已提交的三态</b>（已通过 + 待审核 + 已驳回），不含 PENDING_SUBMIT ——
 * 那批还没提交，不参与审核口径；通过率 = 已通过 / total，与 total 的分母保持一致。
 *
 * <p>passRate 是 0~1 的小数，前端自己乘 100 显示为百分比。
 */
@Data
public class DurationAuditSummaryVO implements Serializable {

    /** 审核总量（三态合计） */
    private long total;

    /** 通过率，0~1 的小数，保留三位 */
    private BigDecimal passRate;

    /** 三态分布，顺序固定：已通过 / 待审核 / 已驳回 */
    private List<DurationSummaryItemVO> items;

    /** 待处理队列长度（等于 items[1].value，前端指标卡单独取用） */
    private long pendingInQueue;
}
