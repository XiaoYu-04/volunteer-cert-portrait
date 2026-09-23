package com.vcp.volunteer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 活动分类新增 / 修改请求（POST、PUT /api/v1/categories）。
 *
 * <p><b>remark 收下但不落库</b>：前端分类管理页有「说明」输入框并会提交，
 * 而 {@code activity_category} 表没有对应列（审计见待办 B16），本轮又不允许改 DDL。
 * 因此该字段仅用于兼容请求体，保存后分类列表里的「说明」列会显示为空（前端渲染为「—」）。
 * 后续补列时应在此处接上，不要只改前端。
 */
@Data
public class CategorySaveDTO implements Serializable {

    @NotBlank(message = "请填写分类名称")
    private String name;

    /** 分类编码，如 COMMUNITY；留空时由服务端按名称生成 */
    private String code;

    /** 排序，升序；为空时排到最后 */
    private Integer sort;

    /** 说明，见类注释：当前不落库 */
    private String remark;
}
