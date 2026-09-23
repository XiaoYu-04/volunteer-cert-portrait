package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 时长审核三态中的一态，对应前端 {@code charts.audit} 的扇区。
 *
 * <p>字段与 {@code dataset.js} 的 {@code audit.items} 对齐：{@code {name, value, tone}}。
 *
 * <p><b>tone 是图表的语义色键</b>（ok 绿 / warn 橙 / bad 红），由
 * {@code charts.audit} 直接查表取色，取不到就是 undefined，扇区会变成默认色 ——
 * 不报错但颜色语义全丢。因此三态的 tone 固定写死在服务层，不随字典配置漂移。
 */
@Data
public class AuditItemVO implements Serializable {

    /** 状态中文名：已通过 / 待审核 / 已驳回 */
    private String name;

    /** 记录条数 */
    private Long value;

    /** 语义色调：ok / warn / bad */
    private String tone;
}
