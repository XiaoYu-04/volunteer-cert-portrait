package com.vcp.volunteer.vo;

import com.vcp.org.vo.OrgVO;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 组织端数据概览响应对象（GET /api/v1/activities/org-overview）。
 *
 * <p>形状与前端 OrgDashboardView 的取数一致：{@code { org, stats }}，
 * 页面用 org 渲染组织资料与「活动场次 / 签到率 / 通过率」，
 * 用 stats 渲染顶部六个指标卡。
 *
 * <p>org 直接复用 vcp-org 的 {@link OrgVO}（跨模块只调对方 Service 接口，
 * 不重新拼一个同构对象）；其中 activities / signRate / passRate 三个聚合字段
 * 由本模块在返回前按本域数据回填，避免页面上显示成 0。
 */
@Data
public class OrgOverviewVO implements Serializable {

    /** 组织资料与聚合指标 */
    private OrgVO org;

    /** 顶部指标卡 */
    private List<StatItemVO> stats;
}
