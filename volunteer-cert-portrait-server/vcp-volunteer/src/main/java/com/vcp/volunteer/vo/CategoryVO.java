package com.vcp.volunteer.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 活动分类响应对象。
 *
 * <p>字段与前端分类管理页的表格列一一对应：sort / name / code / activityCount / remark。
 * 分类接口一次返回全部、不分页（前端就是全量渲染并在前端算合计），因此没有分页壳。
 *
 * <p>{@code activityCount} 是该分类下的活动场次，由聚合查询得到，
 * 前端拿它画进度条并算「覆盖活动」总数，不能返回 null。
 *
 * <p>{@code status} 返回字符串码 ACTIVE / DISABLED（库里的 status 是 SMALLINT，见实体注释）。
 */
@Data
public class CategoryVO implements Serializable {

    private Long id;

    /** 分类名称 */
    private String name;

    /** 分类编码 */
    private String code;

    /** 该分类下的活动场次 */
    private Integer activityCount;

    /** 排序 */
    private Integer sort;

    /** 状态码：ACTIVE / DISABLED */
    private String status;

    /** 说明；列由 sql/07_demo_scale.sql 追加，已真正落库 */
    private String remark;
}
