package com.vcp.analytics.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 看板全量数据，对应前端 {@code GET /v1/analytics/dashboard}。
 *
 * <p>首页与两个控制台看板都用这一个接口：一次拉齐 12 个区块，
 * 避免首屏串行发七八个请求（前端 {@code api/analytics.js} 的注释写明了这个取舍）。
 * 因此这里的字段<b>一个都不能少</b>：少一个字段不会报错，
 * 只会让对应的图表或列表静默变成空白。
 *
 * <p>字段与 {@code mock/data/analytics.js} 的 dashboard 响应逐条对齐：
 * stats / trend / types / colleges / orgs / profiles / audit / signin / heatmap /
 * flow / notices / activities。
 *
 * <p>其中 stats 与其余各区块是<b>同一份口径</b>：例如 stats 里的 signRate 与
 * signin.rate 描述同一个签到率（前者是百分数，后者是 0~1 小数，见各自类注释），
 * 二者由同一批聚合查询派生，不会出现"卡片说 92.3%、仪表盘说 88%"这种自相矛盾。
 */
@Data
public class DashboardVO implements Serializable {

    /** 六个核心指标卡片 */
    private List<StatItemVO> stats;

    /** 近 12 个月的活动量与服务时长趋势 */
    private List<TrendPointVO> trend;

    /** 活动类型占比 */
    private List<TypeShareVO> types;

    /** 学院志愿时长排名 */
    private List<CollegeStatVO> colleges;

    /** 组织活跃度 */
    private List<OrgStatVO> orgs;

    /** 公益画像标签分布 */
    private List<ProfileTagVO> profiles;

    /** 时长审核概览 */
    private AuditVO audit;

    /** 签到率统计 */
    private SigninVO signin;

    /** 单月签到热力日历 */
    private HeatmapVO heatmap;

    /** 服务时长认证流程，固定文案 */
    private List<FlowStepVO> flow;

    /** 当前登录用户的最近通知公告 */
    private List<DashboardNoticeVO> notices;

    /** 开放报名的活动，供首页"本期志愿活动"卡片展示 */
    private List<DashboardActivityVO> activities;
}
