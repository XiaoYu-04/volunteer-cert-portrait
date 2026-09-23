package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 看板上的通知公告条目，对应前端 {@code NoticeList} 组件。
 *
 * <p>字段与 {@code dataset.js} 的 {@code notices} 对齐：{@code {id, title, date, from, top}}，
 * 与 {@code vcp-system} 的通知列表接口同一套命名。
 *
 * <p><b>from 对应 notification.source</b>：列名不能叫 from（SQL 关键字），
 * 出参时映射回 from —— 组件里直接渲染 {@code {{ row.from }}}，
 * 字段名写错只会让"来源"一列空着，不会报错。
 *
 * <p><b>date 是已格式化的 "yyyy-MM-dd" 字符串</b>：页面同样直接渲染 {@code {{ row.date }}}，
 * 返回 ISO 时间串会带上 {@code T}。
 */
@Data
public class DashboardNoticeVO implements Serializable {

    private Long id;

    private String title;

    /** 发布日期，已格式化为 yyyy-MM-dd */
    private String date;

    /** 发布方，取自 notification.source */
    private String from;

    /** 是否置顶 */
    private Boolean top;
}
