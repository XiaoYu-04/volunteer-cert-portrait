package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 组织活跃度，对应前端 {@code charts.orgBar} / {@code charts.orgRadar} 与 {@code OrgList}。
 *
 * <p>字段与 {@code dataset.js} 的 {@code orgs} 对齐：{@code {org, activities, signRate, passRate}}。
 *
 * <p><b>两个比率是 0~1 的小数而不是百分数</b>：前端按
 * {@code Math.round(d.signRate * 100)} 再拼 "%" 渲染，传 94 会显示成 "9400%"。
 * 这与 {@code StatItemVO} 里 signRate/passRate 用百分数是<b>两套口径</b>，
 * 分别对应 mock 的 {@code orgs} 与 {@code stats}，不要顺手统一。
 *
 * <p>只返回<b>有活动记录</b>的组织：待审核组织没有活动，混进活跃度排名只会多出一行
 * 空条（签到率、通过率分母为 0），对"活跃度"没有任何信息量。
 */
@Data
public class OrgStatVO implements Serializable {

    /** 组织名称 */
    private String org;

    /** 活动场次 */
    private Long activities;

    /** 签到率，0~1 小数 */
    private BigDecimal signRate;

    /** 时长审核通过率，0~1 小数 */
    private BigDecimal passRate;
}
