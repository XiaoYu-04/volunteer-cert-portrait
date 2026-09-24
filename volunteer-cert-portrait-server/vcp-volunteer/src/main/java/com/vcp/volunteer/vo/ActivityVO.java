package com.vcp.volunteer.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 活动响应对象，字段名与前端契约（{@code src/mock/data/dataset.js} 的 activities）逐项对齐。
 *
 * <p><b>为什么日期与时间拆成两个字段</b>：前端列表页与详情页都是直接渲染
 * {@code {{ row.date }} {{ row.time }}}（如「2025-03-22 09:00」），
 * 传一个 ISO 时间串过去页面上会多出一截。因此这里把 {@code start_time} 拆成
 * 日期（yyyy-MM-dd）与时间（HH:mm）两段返回，由服务端格式化好。
 *
 * <p>同理 {@code deadline} 只返回日期：表单填的就是日期，前端也按日期展示。
 *
 * <p>{@code enrolled} / {@code capacity} 是库里的 signed_count / max_count 的别名，
 * 前端列表的「46 / 60」直接按这两个字段渲染，改名会让所有列表页显示空白。
 */
@Data
public class ActivityVO implements Serializable {

    private Long id;

    /** 活动名称 */
    private String title;

    /** 活动类型（分类名称），如「社区服务」 */
    private String type;

    /** 活动日期 yyyy-MM-dd */
    private String date;

    /** 开始时间 HH:mm */
    private String time;

    /** 活动地点 */
    private String place;

    /** 已报名人数 */
    private Integer enrolled;

    /** 招募名额 */
    private Integer capacity;

    /** 发布组织名称 */
    private String org;

    /** 单人服务时长（小时） */
    private BigDecimal hours;

    /** 活动状态码 */
    private String status;

    /** 活动分类 id */
    private Long categoryId;

    /** 发布组织 id */
    private Long orgId;

    /** 报名截止日期 yyyy-MM-dd */
    private String deadline;

    /** 联系方式 */
    private String contact;

    /** 活动简介 */
    private String description;

    /** 封面图地址 */
    private String cover;

    /** 活动图文说明，列表接口通常为空，详情接口返回 */
    private List<ActivityImageVO> images;
}
