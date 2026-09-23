package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 看板核心指标卡片，对应前端 {@code InkStat} 组件的入参。
 *
 * <p>字段与 {@code mock/data/dataset.js} 的 {@code stats} 逐条对齐：
 * {@code {key, label, value, unit, delta, trend}}。前端是<b>直接渲染</b>这些值的
 * （{@code {{ item.label }}}、{@code {{ item.value }}}），且 {@code key} 同时用作
 * {@code v-for} 的 key，因此六个指标的 key 必须稳定且唯一。
 *
 * <p><b>value 与 delta 必须是 JSON 数字而不是字符串</b>：{@code InkStat} 把 delta 声明为
 * {@code Number} 并直接调 {@code Math.abs(delta).toFixed(1)}，传字符串会渲染成 NaN；
 * value 会走千分位格式化，传字符串虽然能显示，但会丢掉格式化能力。
 * 故两者都用 {@link BigDecimal}，由 Jackson 序列化成 JSON 数字。
 *
 * <p><b>delta 为 null 时整个环比行不展示</b>（组件里是 {@code v-if="delta !== null"}），
 * 这正是基期为 0（如"上月新增活动为 0"）时应有的表现 —— 不要用 0 代替 null，
 * 0 会渲染成"↑0.0%"，看起来像"确实没变化"，与"无法比较"是两回事。
 */
@Data
public class StatItemVO implements Serializable {

    /** 指标键：activities / enrolled / hours / monthNew / signRate / passRate */
    private String key;

    /** 指标名称，如「活动总数」 */
    private String label;

    /** 指标值；计数与合计为整数或 1 位小数，比率为 1 位小数的百分数 */
    private BigDecimal value;

    /** 单位，如「场」「人」「小时」「%」 */
    private String unit;

    /**
     * 环比变化，含义随指标口径而不同：
     *
     * <p>计数/合计类（activities、enrolled、hours、monthNew）是<b>百分比</b>，
     * 如 12.0 表示比基期增长 12.0%；比率类（signRate、passRate）是<b>百分点</b>，
     * 如 -0.8 表示比基期下降 0.8 个百分点。基期为 0 时无法比较，返回 null。
     */
    private BigDecimal delta;

    /** 环比方向：up 上升 / down 下降；与 delta 同生共死 */
    private String trend;
}
