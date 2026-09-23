package com.vcp.analytics.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.vcp.analytics.service.AnalyticsService;
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
import com.vcp.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据看板接口。全部是 GET 查询，<b>不标 {@code @OperationLog}</b>：
 * 操作日志只记写操作（新增/修改/删除/审核/提交），读接口标了只会把日志表刷成
 * 无意义的看板刷新记录，把真正要审计的动作淹掉。
 *
 * <p><b>权限分两档，原因很实际：</b>
 * <ul>
 *   <li>{@code /dashboard} 只校验登录。学生首页、组织看板、学校看板三个页面
 *       <b>都在调它</b>（前端三个页面共用 {@code getDashboard}），
 *       而 {@code analytics:dashboard:view} 只发给学校管理员（见 StpInterfaceImpl），
 *       挂上去会直接把学生端与组织端打成 20003；</li>
 *   <li>其余九个是学校管理端的单项统计，挂 {@code analytics:dashboard:view}。
 *       它们目前没有页面在调（前端只调 dashboard），是留给后续"单独刷新某一块"的接口，
 *       权限从严不影响任何现有页面。</li>
 * </ul>
 *
 * <p><b>路径全部是静态段</b>（overview / trend / types / …），没有 {@code /{id}} 这类
 * 动态段，因此不存在"静态段被动态段抢先匹配"的问题，也不会与
 * {@code /api/v1/activities/org-overview} 之类的路径冲突。
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * 看板全量数据。
     *
     * <p>首页与控制台一次拉齐，避免首屏串行发七八个请求。数据范围由登录态决定：
     * 通知公告只返回当前用户自己的，其余是全校口径的公开统计。
     *
     * @return 全量看板数据
     */
    @GetMapping("/dashboard")
    @SaCheckLogin
    public R<DashboardVO> dashboard() {
        return R.ok(analyticsService.getDashboard());
    }

    /**
     * 六个核心指标卡片。
     *
     * @return 指标卡片列表
     */
    @GetMapping("/overview")
    @SaCheckPermission("analytics:dashboard:view")
    public R<List<StatItemVO>> overview() {
        return R.ok(analyticsService.getOverview());
    }

    /**
     * 近 12 个月的活动量与服务时长趋势。
     *
     * @return 趋势点列表
     */
    @GetMapping("/trend")
    @SaCheckPermission("analytics:dashboard:view")
    public R<List<TrendPointVO>> trend() {
        return R.ok(analyticsService.getTrend());
    }

    /**
     * 活动类型占比。
     *
     * @return 各分类活动场次
     */
    @GetMapping("/types")
    @SaCheckPermission("analytics:dashboard:view")
    public R<List<TypeShareVO>> types() {
        return R.ok(analyticsService.getTypes());
    }

    /**
     * 各学院志愿时长排名。
     *
     * @return 学院统计列表
     */
    @GetMapping("/colleges")
    @SaCheckPermission("analytics:dashboard:view")
    public R<List<CollegeStatVO>> colleges() {
        return R.ok(analyticsService.getColleges());
    }

    /**
     * 组织活跃度。
     *
     * @return 组织统计列表
     */
    @GetMapping("/orgs")
    @SaCheckPermission("analytics:dashboard:view")
    public R<List<OrgStatVO>> orgs() {
        return R.ok(analyticsService.getOrgs());
    }

    /**
     * 公益画像标签分布。
     *
     * @return 标签分布列表
     */
    @GetMapping("/profiles")
    @SaCheckPermission("analytics:dashboard:view")
    public R<List<ProfileTagVO>> profiles() {
        return R.ok(analyticsService.getProfiles());
    }

    /**
     * 时长审核概览。
     *
     * @return 审核三态计数与通过率
     */
    @GetMapping("/audit")
    @SaCheckPermission("analytics:dashboard:view")
    public R<AuditVO> audit() {
        return R.ok(analyticsService.getAudit());
    }

    /**
     * 签到率统计。
     *
     * @return 应签到、实签到、缺勤与签到率
     */
    @GetMapping("/signin")
    @SaCheckPermission("analytics:dashboard:view")
    public R<SigninVO> signin() {
        return R.ok(analyticsService.getSignin());
    }

    /**
     * 单月签到热力日历。
     *
     * @return 目标月与逐日签到人次
     */
    @GetMapping("/heatmap")
    @SaCheckPermission("analytics:dashboard:view")
    public R<HeatmapVO> heatmap() {
        return R.ok(analyticsService.getHeatmap());
    }
}
