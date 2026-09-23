package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 学生公益画像的标签分布，对应前端 {@code charts.profile} 与 {@code PortraitSeal}。
 *
 * <p>字段与 {@code dataset.js} 的 {@code profiles} 对齐：{@code {tag, count, desc}}。
 *
 * <p><b>desc 是标签的判定规则说明，不是某个学生的画像描述</b>：学生端的印章组件会把
 * 它渲染在标签名下方（{@code {{ item.desc }}}），所以取的是规则文案
 * （如「已完成活动 ≥ 3 场」）。规则见 {@code docs/公益等级与标签规则方案.md}，
 * 库里没有这张"标签→规则"的映射表，故由服务层按同一份规则维护。
 * 库里出现规则外的标签时 desc 为 null，前端渲染成空行，不会报错。
 */
@Data
public class ProfileTagVO implements Serializable {

    /** 标签名，如「热心志愿者」 */
    private String tag;

    /** 拥有该标签的学生人数 */
    private Long count;

    /** 标签判定规则说明；规则外的标签为 null */
    private String desc;
}
