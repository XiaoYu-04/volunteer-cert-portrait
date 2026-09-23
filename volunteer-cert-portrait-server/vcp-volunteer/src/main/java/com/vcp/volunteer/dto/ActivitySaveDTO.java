package com.vcp.volunteer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 活动新增 / 修改请求（POST /api/v1/activities、PUT /api/v1/activities/{id}）。
 *
 * <p>字段按前端 {@code ActivityPublishView} 的表单逐项对齐：日期与时间是两个独立输入框，
 * 服务端合并成 {@code start_time}；结束时间由「开始时间 + hours」推导，不接受前端传值。
 *
 * <p><b>type 与 org 是前端顺手带上的名称</b>（分类名、组织名），服务端一律忽略并按
 * categoryId / 登录态重新解析，避免前端传什么就存什么。
 *
 * <p>修改接口用同一个 DTO，但除活动名称外都允许缺省：前端当前只用到创建，
 * 修改接口按「传了就更新」处理，避免为将来可能的分步编辑先写死一套全量校验。
 */
@Data
public class ActivitySaveDTO implements Serializable {

    @NotBlank(message = "请填写活动名称")
    private String title;

    /** 活动分类 id */
    private Long categoryId;

    /** 活动日期 yyyy-MM-dd */
    private String date;

    /** 开始时间 HH:mm */
    private String time;

    /** 活动地点 */
    private String place;

    /** 招募名额，0 表示不限 */
    @Min(value = 0, message = "招募名额不能小于 0")
    private Integer capacity;

    /** 单人服务时长（小时） */
    private BigDecimal hours;

    /** 报名截止日期 yyyy-MM-dd */
    private String deadline;

    /** 联系方式 */
    private String contact;

    /** 活动简介 */
    private String description;

    /** 发布组织 id：组织管理员忽略该值，一律用登录态里的 orgId */
    private Long orgId;

    /** 分类名称，仅用于兼容前端表单，服务端忽略 */
    private String type;

    /** 组织名称，仅用于兼容前端表单，服务端忽略 */
    private String org;
}
