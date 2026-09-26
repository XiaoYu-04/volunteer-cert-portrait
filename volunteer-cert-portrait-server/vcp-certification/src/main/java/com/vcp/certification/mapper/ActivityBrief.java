package com.vcp.certification.mapper;

import lombok.Data;

import java.io.Serializable;

/**
 * 活动的精简读模型：提交时长时用于校验活动存在性与归属，并取权威的活动名称与分类名。
 *
 * <p>为什么需要它：活动与分类归 vcp-volunteer 域，而 vcp-volunteer 与本模块并行开发中
 * （尚未落地），本模块无法调用其 Service 接口；但按模块依赖方向
 * {@code vcp-certification → vcp-volunteer} 是合法方向，读它的表拼出业务字段是允许的。
 * 本类只承载查询结果，不参与入参校验。
 */
@Data
public class ActivityBrief implements Serializable {

    /** 活动名称 */
    private String title;

    /** 发布组织 → org_info.id；为空表示活动未挂组织 */
    private Long orgId;

    /** 活动分类名，如「社区服务」；活动未挂分类时为空 */
    private String categoryName;
}
