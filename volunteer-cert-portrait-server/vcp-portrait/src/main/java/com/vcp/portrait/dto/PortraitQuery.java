package com.vcp.portrait.dto;

import com.vcp.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 画像明细查询条件（{@code GET /api/v1/portraits}）。
 *
 * <p>字段名与前端 {@code PortraitView.vue} 的筛选栏逐条对应：
 * {@code keyword}（学生姓名或学号）、{@code college}（学院，精确匹配）、
 * {@code tag}（画像标签，取值来自 {@code /portraits/distribution} 的下拉框）。
 * 分页参数 {@code page} / {@code pageSize} 由基类提供，不要改名。
 *
 * <p>三个筛选参数都允许为空串 —— 前端「重置」会把它们置成 {@code ''}，
 * 而空串必须被当作「不筛选」而不是「筛空值」，归一化在 Service 里做。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PortraitQuery extends PageQuery {

    /** 关键字，匹配学生姓名或学号 */
    private String keyword;

    /** 学院，精确匹配 */
    private String college;

    /** 画像标签 */
    private String tag;
}
