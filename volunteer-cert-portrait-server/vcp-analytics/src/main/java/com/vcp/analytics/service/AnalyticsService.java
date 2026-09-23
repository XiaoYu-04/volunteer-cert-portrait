package com.vcp.analytics.service;

import com.vcp.analytics.vo.AuditVO;
import com.vcp.analytics.vo.CollegeStatVO;
import com.vcp.analytics.vo.DashboardVO;
import com.vcp.analytics.vo.HeatmapVO;
import com.vcp.analytics.vo.OrgStatVO;
import com.vcp.analytics.vo.ProfileTagVO;
import com.vcp.analytics.vo.SigninVO;
import com.vcp.analytics.vo.StatItemVO;
import com.vcp.analytics.vo.TrendPointVO;
import com.vcp.analytics.vo.TypeShareVO;

import java.util.List;

/**
 * 看板统计服务。
 *
 * <p>十个方法对应前端的十个接口：{@code dashboard} 是首页与控制台一次拉齐的全量数据，
 * 其余九个是把 dashboard 拆开后的单项数据（前端目前只调 dashboard，
 * 拆分接口保留给后续页面单独刷新某一块用）。
 *
 * <p><b>没有写方法</b>：本模块只读聚合、无独立表，所有方法都是查询。
 */
public interface AnalyticsService {

    /**
     * 看板全量数据：一次返回首页与控制台需要的全部区块。
     *
     * @return 全量看板数据
     */
    DashboardVO getDashboard();

    /**
     * 六个核心指标卡片。
     *
     * @return 指标卡片，顺序固定
     */
    List<StatItemVO> getOverview();

    /**
     * 近 12 个月的活动量与服务时长趋势。
     *
     * @return 趋势点，按月份升序，无数据的月份为 0
     */
    List<TrendPointVO> getTrend();

    /**
     * 活动类型占比。
     *
     * @return 各分类活动场次，按场次降序
     */
    List<TypeShareVO> getTypes();

    /**
     * 各学院志愿时长排名。
     *
     * @return 学院统计，按时长降序
     */
    List<CollegeStatVO> getColleges();

    /**
     * 组织活跃度。
     *
     * @return 组织统计，按活动场次降序
     */
    List<OrgStatVO> getOrgs();

    /**
     * 公益画像标签分布。
     *
     * @return 标签分布，按人数降序
     */
    List<ProfileTagVO> getProfiles();

    /**
     * 时长审核概览。
     *
     * @return 审核三态计数与通过率
     */
    AuditVO getAudit();

    /**
     * 签到率统计。
     *
     * @return 应签到、实签到、缺勤与签到率
     */
    SigninVO getSignin();

    /**
     * 单月签到热力日历。
     *
     * @return 目标月与逐日签到人次
     */
    HeatmapVO getHeatmap();
}
