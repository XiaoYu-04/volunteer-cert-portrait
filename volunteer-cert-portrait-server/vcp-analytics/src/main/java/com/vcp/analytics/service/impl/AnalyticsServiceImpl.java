package com.vcp.analytics.service.impl;

import com.vcp.analytics.mapper.AnalyticsMapper;
import com.vcp.analytics.service.AnalyticsService;
import com.vcp.analytics.vo.AuditCountRowVO;
import com.vcp.analytics.vo.AuditItemVO;
import com.vcp.analytics.vo.AuditVO;
import com.vcp.analytics.vo.CollegeStatVO;
import com.vcp.analytics.vo.DashboardVO;
import com.vcp.analytics.vo.FlowStepVO;
import com.vcp.analytics.vo.HeatmapDayVO;
import com.vcp.analytics.vo.HeatmapVO;
import com.vcp.analytics.vo.MetricRowVO;
import com.vcp.analytics.vo.OrgStatVO;
import com.vcp.analytics.vo.ProfileTagVO;
import com.vcp.analytics.vo.SigninVO;
import com.vcp.analytics.vo.StatItemVO;
import com.vcp.analytics.vo.TrendPointVO;
import com.vcp.analytics.vo.TypeShareVO;
import com.vcp.framework.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 看板统计服务实现。
 *
 * <p><b>本类不做聚合</b>：计数、求和、分组、按天补齐全部由 {@link AnalyticsMapper} 的
 * 单条 SQL 完成，这里只做三件事 ——
 * <ol>
 *   <li>把聚合结果装配成前端契约要求的嵌套结构（如审核三态明细、热力图的日数组）；</li>
 *   <li>用 SQL 给好的两个数字算环比（纯算术，不碰明细）；</li>
 *   <li>补齐库里没有的说明性文案（标签规则说明、认证流程），见各自常量。</li>
 * </ol>
 *
 * <p><b>卡片文案与单位写在这里而不是 SQL 里</b>：它们是展示层文案，改文案不该动 SQL。
 * 指标清单（六个卡片的顺序、键、名称、单位）是前端契约的一部分，顺序固定为
 * 活动总数 → 报名总人数 → 累计志愿时长 → 本月新增活动 → 签到率 → 时长审核通过率。
 *
 * <p>所有金额/比率都用 {@link BigDecimal} 而不是 double：比率要参与除法与四舍五入，
 * 用 double 会出现 0.9230000000000001 这类尾数，前端乘 100 之后偶尔少 0.1。
 */
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    /** 环比方向：上升 */
    private static final String TREND_UP = "up";

    /** 环比方向：下降 */
    private static final String TREND_DOWN = "down";

    /** 审核三态的语义色调，与前端 {@code charts.audit} 的取色表一致 */
    private static final String TONE_OK = "ok";

    private static final String TONE_WARN = "warn";

    private static final String TONE_BAD = "bad";

    /** 看板公告栏条数，与前端 {@code NoticeList} 的 limit 一致 */
    private static final int DASHBOARD_NOTICE_LIMIT = 5;

    /** 看板"本期志愿活动"条数，与前端首页 {@code slice(0, 6)} 一致 */
    private static final int DASHBOARD_ACTIVITY_LIMIT = 6;

    /** 环比保留 1 位小数，与 {@code InkStat} 的 {@code toFixed(1)} 对齐 */
    private static final int DELTA_SCALE = 1;

    /** 百分之百，用于把比率换算成百分数 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * 标签 → 判定规则说明。
     *
     * <p>规则来自 {@code docs/公益等级与标签规则方案.md}（已确认），库里只有标签名，
     * 没有这张映射表，故在服务层维护一份；改动规则时要与规则文档、vcp-portrait 的
     * 标签生成逻辑一起改。
     *
     * <p>用 Map 而不是 if-else：标签将来可能继续增加，这里加一行即可；
     * 查不到的标签 desc 为 null，前端渲染成空行，不会报错。
     */
    private static final Map<String, String> TAG_RULES = Map.ofEntries(
            Map.entry("热心志愿者", "累计有效时长 > 0 小时"),
            Map.entry("长期坚持型", "已完成活动 ≥ 3 场"),
            Map.entry("校园服务型", "校园服务类参与最多"),
            Map.entry("社区服务型", "社区服务类参与最多"),
            Map.entry("环保行动型", "环保公益类参与最多"),
            Map.entry("大型活动型", "大型赛事类参与最多"),
            Map.entry("助老服务型", "助老服务类参与最多"),
            Map.entry("文化传播型", "文化传播类参与最多")
    );

    private final AnalyticsMapper analyticsMapper;

    /**
     * 看板快照缓存：key = 用户 id，value = 装配好的 VO + 写入时刻。
     * 详见 {@link #getDashboard()} 里的说明；TTL 设为 0 即等于关闭缓存。
     */
    private final Map<Long, CachedDashboard> dashboardCache = new ConcurrentHashMap<>();

    /** 看板快照有效期（毫秒）。设为 0 可关闭缓存、恢复"每次都查库"。 */
    private static final long DASHBOARD_CACHE_TTL_MS = 5_000L;

    /** 缓存条目上限，超过即整体清空（防止异常情况下无限增长）。 */
    private static final int DASHBOARD_CACHE_MAX_ENTRIES = 200;

    @Override
    public DashboardVO getDashboard() {
        // ---------------------------------------------------------------
        // 看板快照缓存（2026-09-27 性能优化）
        //
        // 为什么加：云库是远程实例，实测**每条 SQL 一次往返约 65ms**（SQL 本身只有
        // 0.05~5ms），而看板一次要串行跑 11 条聚合 → 单次请求约 800ms，是整站最慢的接口。
        // 仪表盘天然容忍秒级延迟（管理员看到的分布图不会因为晚 5 秒而变化），
        // 因此按「用户」缓存装配好的 VO 5 秒：连续刷新、多人同时看板都只打一次库。
        //
        // 边界与代价（刻意写清楚，别被当成"数据不刷新"的 bug）：
        //   · 只缓存读路径，且只缓存 getDashboard 这一个接口；写接口一律实时落库；
        //   · 公告块按收件人过滤，所以缓存键是 userId（不同人不会串号）；
        //   · 数据变更后最多 5 秒才在看板体现 —— 这是本缓存唯一的语义变化；
        //   · 缓存条目上限 200，超过直接清空（本系统管理员数量远小于该值，
        //     纯粹是防止异常情况下 Map 无限增长）。
        // 想彻底关掉：把 DASHBOARD_CACHE_TTL_MS 设为 0 即可（等价于每次都查库）。
        // ---------------------------------------------------------------
        long userId = AuthUtils.getUserId();
        long now = System.currentTimeMillis();
        CachedDashboard hit = dashboardCache.get(userId);
        if (hit != null && now - hit.at() < DASHBOARD_CACHE_TTL_MS) {
            return hit.vo();
        }
        DashboardVO dashboard = buildDashboard();
        dashboardCache.put(userId, new CachedDashboard(dashboard, now));
        if (dashboardCache.size() > DASHBOARD_CACHE_MAX_ENTRIES) {
            dashboardCache.clear();
        }
        return dashboard;
    }

    /** 看板快照缓存的条目：装配好的 VO + 写入时刻（毫秒）。 */
    private record CachedDashboard(DashboardVO vo, long at) {}

    /**
     * 真正查库装配看板。与 2026-09-27 之前的 {@code getDashboard} 逐行一致，
     * 只是抽出来给缓存调用，便于「有缓存 / 无缓存」两条路径都走同一段逻辑。
     */
    private DashboardVO buildDashboard() {
        DashboardVO dashboard = new DashboardVO();
        // 十一个聚合查询各自独立，按契约逐块装配；任何一块为空都只影响它自己，
        // 不会让整个看板 500（列表类接口返回空列表而不是 null，前端有 || [] 兜底）
        dashboard.setStats(getOverview());
        dashboard.setTrend(getTrend());
        dashboard.setTypes(getTypes());
        dashboard.setColleges(getColleges());
        dashboard.setOrgs(getOrgs());
        dashboard.setProfiles(getProfiles());
        dashboard.setAudit(getAudit());
        dashboard.setSignin(getSignin());
        dashboard.setHeatmap(getHeatmap());
        dashboard.setFlow(buildFlow());
        // 公告按收件人过滤，看板上看到的是"我"的通知，不串号
        dashboard.setNotices(analyticsMapper.selectRecentNotices(AuthUtils.getUserId(), DASHBOARD_NOTICE_LIMIT));
        dashboard.setActivities(analyticsMapper.selectOpenActivities(DASHBOARD_ACTIVITY_LIMIT));
        return dashboard;
    }

    @Override
    public List<StatItemVO> getOverview() {
        Map<String, MetricRowVO> metrics = indexByKey(analyticsMapper.selectOverviewMetrics());

        List<StatItemVO> items = new ArrayList<>(6);
        // 前三张是累计量：环比 = 本期相对上月末的变化率（百分比）
        items.add(percentDeltaCard("activities", "活动总数", "场", metrics));
        items.add(percentDeltaCard("enrolled", "报名总人数", "人", metrics));
        items.add(percentDeltaCard("hours", "累计志愿时长", "小时", metrics));
        // 本月新增活动本身就是月度量：环比 = 本月相对上月的变化率（百分比）
        items.add(percentDeltaCard("monthNew", "本月新增活动", "场", metrics));
        // 后两张是比率：环比用"百分点"差，不用百分比变化 ——
        // 从 90% 到 92.3% 说"提高 2.3 个百分点"是对的，说"提高 2.6%"要绕一层
        items.add(pointDeltaCard("signRate", "签到率", "%", metrics));
        items.add(pointDeltaCard("passRate", "时长审核通过率", "%", metrics));
        return items;
    }

    @Override
    public List<TrendPointVO> getTrend() {
        return analyticsMapper.selectMonthlyTrend();
    }

    @Override
    public List<TypeShareVO> getTypes() {
        return analyticsMapper.selectTypeShares();
    }

    @Override
    public List<CollegeStatVO> getColleges() {
        return analyticsMapper.selectCollegeStats();
    }

    @Override
    public List<OrgStatVO> getOrgs() {
        return analyticsMapper.selectOrgStats();
    }

    @Override
    public List<ProfileTagVO> getProfiles() {
        List<ProfileTagVO> tags = analyticsMapper.selectProfileTagCounts();
        // 补上标签的规则说明：库里只有标签名，说明文案按规则文档在服务层维护
        for (ProfileTagVO tag : tags) {
            tag.setDesc(TAG_RULES.get(tag.getTag()));
        }
        return tags;
    }

    @Override
    public AuditVO getAudit() {
        AuditCountRowVO row = analyticsMapper.selectAuditCounts();
        AuditVO audit = new AuditVO();
        audit.setTotal(count(row == null ? null : row.getTotal()));
        audit.setPassRate(row == null || row.getPassRate() == null ? BigDecimal.ZERO : row.getPassRate());
        // 顺序即契约：看板页把 items[1] 当"审核待处理"，见 AuditVO 的类注释
        audit.setItems(List.of(
                auditItem("已通过", count(row == null ? null : row.getApproved()), TONE_OK),
                auditItem("待审核", count(row == null ? null : row.getPending()), TONE_WARN),
                auditItem("已驳回", count(row == null ? null : row.getRejected()), TONE_BAD)
        ));
        return audit;
    }

    @Override
    public SigninVO getSignin() {
        SigninVO summary = analyticsMapper.selectSigninSummary();
        // 聚合查询（无 GROUP BY）必定返回一行，这里是纯防御：真返回 null 时也不能把 null
        // 交给前端去乘 100 —— 仪表盘会渲染成 NaN%，比显示 0% 更难看
        return summary == null ? emptySignin() : summary;
    }

    @Override
    public HeatmapVO getHeatmap() {
        List<HeatmapDayVO> rows = analyticsMapper.selectMonthlyHeatmap();
        HeatmapVO heatmap = new HeatmapVO();
        // generate_series 至少产出一天，正常到不了这个分支；真到了也不能返回 null，
        // 前端拿到 null 会直接不渲染日历
        if (rows.isEmpty()) {
            heatmap.setDays(List.of());
            return heatmap;
        }
        // 行已按 day 升序，与日历的日序一一对应；count 由 SQL 补齐为 0，不会是 null
        heatmap.setMonth(rows.get(0).getMonth());
        heatmap.setDays(rows.stream().map(row -> row.getCount() == null ? 0 : row.getCount()).toList());
        return heatmap;
    }

    /**
     * 把聚合结果按指标键建索引。
     *
     * @param rows 六个指标的聚合行
     * @return 指标键 → 聚合行
     */
    private static Map<String, MetricRowVO> indexByKey(List<MetricRowVO> rows) {
        Map<String, MetricRowVO> metrics = new HashMap<>(rows.size());
        for (MetricRowVO row : rows) {
            metrics.put(row.getMetricKey(), row);
        }
        return metrics;
    }

    /**
     * 组装一张"计数/合计"卡片，环比按百分比算。
     *
     * @param key     指标键
     * @param label   指标名称
     * @param unit    单位
     * @param metrics 聚合结果索引
     * @return 指标卡片
     */
    private static StatItemVO percentDeltaCard(String key, String label, String unit,
                                               Map<String, MetricRowVO> metrics) {
        MetricRowVO row = metrics.get(key);
        BigDecimal value = row == null ? null : row.getMetricValue();
        BigDecimal base = row == null ? null : row.getPrevValue();
        StatItemVO item = baseCard(key, label, unit, value);
        // 基期为 0 时环比无意义：null 会让整个环比行不展示，而 0 会显示"↑0.0%"，
        // 看起来像"确实没变化"，与"无法比较"是两回事
        if (value != null && base != null && base.signum() != 0) {
            setDelta(item, value.subtract(base).multiply(HUNDRED).divide(base, DELTA_SCALE, RoundingMode.HALF_UP));
        }
        return item;
    }

    /**
     * 组装一张"比率"卡片，环比按百分点差算。
     *
     * @param key     指标键
     * @param label   指标名称
     * @param unit    单位
     * @param metrics 聚合结果索引
     * @return 指标卡片
     */
    private static StatItemVO pointDeltaCard(String key, String label, String unit,
                                             Map<String, MetricRowVO> metrics) {
        MetricRowVO row = metrics.get(key);
        BigDecimal value = row == null ? null : row.getMetricValue();
        BigDecimal base = row == null ? null : row.getPrevValue();
        StatItemVO item = baseCard(key, label, unit, value);
        // 基期为空说明分母为 0（当月或上月没有可统计的记录），此时不展示环比
        if (value != null && base != null) {
            setDelta(item, value.subtract(base).setScale(DELTA_SCALE, RoundingMode.HALF_UP));
        }
        return item;
    }

    /**
     * 卡片公共字段。
     *
     * @param key   指标键
     * @param label 指标名称
     * @param unit  单位
     * @param value 指标值
     * @return 指标卡片
     */
    private static StatItemVO baseCard(String key, String label, String unit, BigDecimal value) {
        StatItemVO item = new StatItemVO();
        item.setKey(key);
        item.setLabel(label);
        item.setUnit(unit);
        // value 是 InkStat 的必填属性，null 会让卡片整块渲染不出来，兜成 0 更稳
        item.setValue(value == null ? BigDecimal.ZERO : value);
        return item;
    }

    /**
     * 写入环比与方向。
     *
     * @param item  指标卡片
     * @param delta 环比值
     */
    private static void setDelta(StatItemVO item, BigDecimal delta) {
        item.setDelta(delta);
        // 0 归到 up：显示"↑0.0%"比"↓0.0%"自然
        item.setTrend(delta.signum() < 0 ? TREND_DOWN : TREND_UP);
    }

    /**
     * 组装审核三态的一项。
     *
     * @param name  中文状态名
     * @param value 条数
     * @param tone  语义色调
     * @return 三态明细项
     */
    private static AuditItemVO auditItem(String name, long value, String tone) {
        AuditItemVO item = new AuditItemVO();
        item.setName(name);
        item.setValue(value);
        item.setTone(tone);
        return item;
    }

    /**
     * 空表时的签到统计，避免把 null 交给前端做乘法。
     *
     * @return 全 0 的签到统计
     */
    private static SigninVO emptySignin() {
        SigninVO signin = new SigninVO();
        signin.setRate(BigDecimal.ZERO);
        signin.setSigned(0L);
        signin.setTotal(0L);
        signin.setAbsent(0L);
        return signin;
    }

    /**
     * 认证流程的四步说明。
     *
     * <p>流程由代码里的状态机决定，库里没有对应的配置表，四行静态文案不值得建表。
     * 内容与 {@code mock/data/dataset.js} 的 flow 保持一致，改动需两边同时改。
     *
     * @return 四步流程
     */
    private static List<FlowStepVO> buildFlow() {
        return List.of(
                flowStep("浏览活动", "按类型与时间筛选，查看活动详情与剩余名额"),
                flowStep("报名审核", "提交报名后由组织管理员审核，结果站内通知"),
                flowStep("签到签退", "现场扫码签到签退，系统自动记录实际服务时长"),
                flowStep("时长认证", "组织提交时长 → 学校管理员审核 → 计入公益画像")
        );
    }

    /**
     * 组装流程的一步。
     *
     * @param step 步骤名
     * @param desc 步骤说明
     * @return 流程步骤
     */
    private static FlowStepVO flowStep(String step, String desc) {
        FlowStepVO item = new FlowStepVO();
        item.setStep(step);
        item.setDesc(desc);
        return item;
    }

    /**
     * 计数取空安全值。
     *
     * @param value 计数
     * @return 原值；为 null 时返回 0
     */
    private static long count(Long value) {
        return value == null ? 0L : value;
    }
}
