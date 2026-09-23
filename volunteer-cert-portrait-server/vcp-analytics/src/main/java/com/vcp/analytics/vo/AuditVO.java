package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 服务时长审核概览，对应前端 {@code charts.audit} 与看板的"审核待处理"数字。
 *
 * <p>字段与 {@code dataset.js} 的 {@code audit} 对齐：{@code {total, passRate, items}}。
 *
 * <p><b>items 的顺序是契约的一部分</b>：看板页直接读
 * {@code data?.audit?.items?.[1]?.value} 当作"审核待处理"的数值，
 * 即下标 1 必须是"待审核"。三态顺序固定为 已通过 → 待审核 → 已驳回，
 * 调换顺序不会报错，只会让"审核待处理"显示成"已通过"的条数。
 *
 * <p>口径：total 与 passRate 只统计<b>已提交</b>的记录（APPROVED / PENDING_AUDIT /
 * REJECTED），不含 PENDING_SUBMIT 的待提交记录 —— 组织还没提交的时长不属于审核工作量。
 * passRate = 已通过 / 已提交，<b>待审核计入分母</b>：它与 total 是同一批记录，
 * 两者必须能对上（mock 的 0.886 就是 2142 / 2418），而且"待审核"本身也只是"还没通过"。
 * 这条口径与看板卡片、组织活跃度里的通过率完全一致。
 */
@Data
public class AuditVO implements Serializable {

    /** 已提交的时长记录总条数 */
    private Long total;

    /** 审核通过率，0~1 小数（已通过 / 已提交） */
    private BigDecimal passRate;

    /** 三态明细，顺序固定：已通过 / 待审核 / 已驳回 */
    private List<AuditItemVO> items;
}
