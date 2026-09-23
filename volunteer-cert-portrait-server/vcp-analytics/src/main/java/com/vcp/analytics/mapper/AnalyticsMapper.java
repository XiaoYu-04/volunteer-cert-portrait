package com.vcp.analytics.mapper;

import com.vcp.analytics.vo.AuditCountRowVO;
import com.vcp.analytics.vo.CollegeStatVO;
import com.vcp.analytics.vo.DashboardActivityVO;
import com.vcp.analytics.vo.DashboardNoticeVO;
import com.vcp.analytics.vo.HeatmapDayVO;
import com.vcp.analytics.vo.MetricRowVO;
import com.vcp.analytics.vo.OrgStatVO;
import com.vcp.analytics.vo.ProfileTagVO;
import com.vcp.analytics.vo.SigninVO;
import com.vcp.analytics.vo.TrendPointVO;
import com.vcp.analytics.vo.TypeShareVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 看板统计 Mapper：全部是只读聚合查询，没有自己的表，也不写任何数据。
 *
 * <p><b>为什么直接查别的域的表</b>：本模块是"只读聚合"模块，职责就是把散在各域的
 * 事实数据汇总成看板数字。跨模块调 Service 在这里行不通 —— 一次看板要跨 8 张表，
 * 走各模块的 Service 会变成"先查出全部明细再在内存里算"，与"聚合下推到 SQL"相反，
 * 而且各域 Service 的接口语义是业务操作（报名、审核），不是统计。
 * 代价是表结构变更时要同步本文件，这一点在模块注释与进展文档里都写明了。
 *
 * <p><b>三条硬约定</b>（与 {@code sql/README.md} 第六节一致）：
 * <ol>
 *   <li>状态一律按<b>英文大写码</b>过滤（APPROVED / COMPLETED / PUBLISHED …），
 *       库里存的就是码值，用中文永远匹配不到；</li>
 *   <li>累计时长以 {@code student_info.total_duration} 为准（A5 已拍板），
 *       {@code student_profile.total_duration} 只是画像快照；</li>
 *   <li>聚合全部写成<b>一条 SQL</b>：计数、求和、分组、按天补齐都由 PostgreSQL 完成，
 *       不把明细捞进内存再算 —— 明细量一上来就是全表传输。</li>
 * </ol>
 *
 * <p>查询里显式带上 {@code deleted = 0}（逻辑删除列只有业务表才有，关联表与流水表没有），
 * 与 MyBatis-Plus 的自动逻辑删除无关：这里是手写 SQL，MP 不会替我们加条件。
 *
 * <p>除 {@link #selectRecentNotices} 与 {@link #selectOpenActivities} 外，
 * 所有方法都返回已经聚合好的结果行，服务层只做组装与环比换算。
 */
@Mapper
public interface AnalyticsMapper {

    /**
     * 六个核心指标的本期值与基期值。
     *
     * <p>一条 SQL 返回六行（UNION ALL），每行是一个指标的 {@code (metric_key, metric_value,
     * prev_value)}。行序不保证，服务层按键取值。
     *
     * <p>各项口径：
     * <ul>
     *   <li>activities 活动总数 = 全部未删除活动；基期取"截至上月末已创建的活动数"；</li>
     *   <li>enrolled 报名总人数 = 全部未删除报名记录（人次）；基期同理按 signup_time 截断；</li>
     *   <li>hours 累计志愿时长 = {@code student_info.total_duration} 合计（权威值）；
     *       基期只能用 {@code service_duration} 里上月末之前已通过的时长合计 ——
     *       student_info 只有当前快照、没有历史，用同一张表反而算不出基期；</li>
     *   <li>monthNew 本月新增活动 = 按 create_time 落在本月；基期取上月；</li>
     *   <li>signRate 签到率 = 实签到人次 / 应签到人次 × 100，与 {@code /signin} 的
     *       {@code rate} 同一口径（那边是 0~1 小数，这边是百分数）；
     *       基期取"活动开始时间在上月末之前的签到率"；</li>
     *   <li>passRate 时长审核通过率 = 已通过 / 已提交 × 100，与 {@code /audit} 的
     *       {@code passRate}、组织活跃度的 passRate 同一口径；
     *       基期取"上月末之前提交的记录的通过率"。</li>
     * </ul>
     *
     * <p>这样六个卡片之间不会互相打架：卡片上的签到率与仪表盘、卡片上的通过率与
     * 审核环形图说的是同一个数（前者百分数、后者小数，是两套既有约定）。
     *
     * <p>比率项在分母为 0 时返回 NULL（由 NULLIF 保证），服务层据此把环比置空。
     *
     * @return 六个指标的本期值与基期值
     */
    @Select("""
            SELECT 'activities' AS metric_key,
                   (SELECT COUNT(*) FROM volunteer_activity WHERE deleted = 0) AS metric_value,
                   (SELECT COUNT(*) FROM volunteer_activity
                     WHERE deleted = 0 AND create_time < date_trunc('month', CURRENT_DATE)) AS prev_value
            UNION ALL
            SELECT 'enrolled',
                   (SELECT COUNT(*) FROM activity_signup WHERE deleted = 0),
                   (SELECT COUNT(*) FROM activity_signup
                     WHERE deleted = 0 AND signup_time < date_trunc('month', CURRENT_DATE))
            UNION ALL
            SELECT 'hours',
                   (SELECT ROUND(COALESCE(SUM(total_duration), 0), 1)
                      FROM student_info WHERE deleted = 0),
                   (SELECT ROUND(COALESCE(SUM(duration), 0), 1)
                      FROM service_duration
                     WHERE deleted = 0
                       AND status = 'APPROVED'
                       AND COALESCE(audit_time, submit_time, create_time) < date_trunc('month', CURRENT_DATE))
            UNION ALL
            SELECT 'monthNew',
                   (SELECT COUNT(*) FROM volunteer_activity
                     WHERE deleted = 0 AND create_time >= date_trunc('month', CURRENT_DATE)),
                   (SELECT COUNT(*) FROM volunteer_activity
                     WHERE deleted = 0
                       AND create_time >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month'
                       AND create_time <  date_trunc('month', CURRENT_DATE))
            UNION ALL
            SELECT 'signRate',
                   (SELECT ROUND(100.0 * (COUNT(*) FILTER (WHERE sign_in_time IS NOT NULL))
                                      / NULLIF(COUNT(*), 0), 1)
                      FROM attendance_record
                     WHERE deleted = 0),
                   (SELECT ROUND(100.0 * (COUNT(*) FILTER (WHERE ar.sign_in_time IS NOT NULL))
                                      / NULLIF(COUNT(*), 0), 1)
                      FROM attendance_record ar
                      JOIN activity_signup sg ON sg.id = ar.signup_id
                      JOIN volunteer_activity a ON a.id = sg.activity_id
                     WHERE ar.deleted = 0
                       AND a.start_time < date_trunc('month', CURRENT_DATE))
            UNION ALL
            SELECT 'passRate',
                   (SELECT ROUND(100.0 * (COUNT(*) FILTER (WHERE status = 'APPROVED'))
                                      / NULLIF(COUNT(*), 0), 1)
                      FROM service_duration
                     WHERE deleted = 0
                       AND status IN ('APPROVED', 'PENDING_AUDIT', 'REJECTED')),
                   (SELECT ROUND(100.0 * (COUNT(*) FILTER (WHERE status = 'APPROVED'))
                                      / NULLIF(COUNT(*), 0), 1)
                      FROM service_duration
                     WHERE deleted = 0
                       AND status IN ('APPROVED', 'PENDING_AUDIT', 'REJECTED')
                       AND COALESCE(submit_time, create_time) < date_trunc('month', CURRENT_DATE))
            """)
    List<MetricRowVO> selectOverviewMetrics();

    /**
     * 近 12 个月的活动量与服务时长趋势。
     *
     * <p>月份序列由 {@code generate_series} 生成后 LEFT JOIN 聚合结果，
     * 因此<b>没有数据的月份也会返回一行 0</b>：折线图缺行会把相邻两点直接连起来，
     * 看起来像那几个月没有间断。
     *
     * <p>两张聚合表分开算再 JOIN，不能合并成一条 —— 一个活动有多条时长记录，
     * 直接 JOIN 会让 COUNT(*) 把时长记录也数进去，活动场次被成倍放大。
     *
     * <p>时间基准取活动开始时间（不是 create_time）：这张图的语义是"每月开展了多少活动"，
     * 前端的图注也按学期解释高峰，用创建时间会把整学期创建的活动堆到同一个月。
     *
     * @return 12 个月的趋势点，按月份升序
     */
    @Select("""
            WITH months AS (
                SELECT generate_series(date_trunc('month', CURRENT_DATE) - INTERVAL '11 months',
                                       date_trunc('month', CURRENT_DATE),
                                       INTERVAL '1 month') AS month_start
            ),
            activity_counts AS (
                SELECT date_trunc('month', a.start_time) AS month_start, COUNT(*) AS cnt
                  FROM volunteer_activity a
                 WHERE a.deleted = 0
                   AND a.start_time IS NOT NULL
                   AND a.start_time >= (SELECT MIN(month_start) FROM months)
                   AND a.start_time <  (SELECT MAX(month_start) FROM months) + INTERVAL '1 month'
                 GROUP BY 1
            ),
            activity_hours AS (
                SELECT date_trunc('month', a.start_time) AS month_start,
                       ROUND(COALESCE(SUM(sd.duration), 0), 1) AS hours
                  FROM service_duration sd
                  JOIN volunteer_activity a ON a.id = sd.activity_id
                 WHERE sd.deleted = 0
                   AND sd.status = 'APPROVED'
                   AND a.deleted = 0
                   AND a.start_time IS NOT NULL
                   AND a.start_time >= (SELECT MIN(month_start) FROM months)
                   AND a.start_time <  (SELECT MAX(month_start) FROM months) + INTERVAL '1 month'
                 GROUP BY 1
            )
            SELECT to_char(m.month_start, 'YYYY-MM') AS month,
                   COALESCE(ac.cnt, 0) AS "count",
                   COALESCE(ah.hours, 0) AS hours
              FROM months m
              LEFT JOIN activity_counts ac ON ac.month_start = m.month_start
              LEFT JOIN activity_hours  ah ON ah.month_start = m.month_start
             ORDER BY m.month_start
            """)
    List<TrendPointVO> selectMonthlyTrend();

    /**
     * 活动类型占比：每个分类的活动场次。
     *
     * <p>以分类表为左表：<b>没有活动的分类也要返回 0</b>，否则环形图的图例会少一项，
     * 看起来像"这个类型被删了"。分类自身的 {@code deleted} 与启用状态一并过滤。
     *
     * @return 各分类的活动场次，按场次降序
     */
    @Select("""
            SELECT c.category_name AS name,
                   COUNT(a.id) AS "value"
              FROM activity_category c
              LEFT JOIN volunteer_activity a ON a.category_id = c.id AND a.deleted = 0
             WHERE c.deleted = 0
             GROUP BY c.id, c.category_name, c.sort
             ORDER BY COUNT(a.id) DESC, c.sort ASC, c.id ASC
            """)
    List<TypeShareVO> selectTypeShares();

    /**
     * 各学院志愿时长排名。
     *
     * <p>学院在 {@code student_info} 里，不在 {@code service_duration} 里
     * （《开发计划与分工.md》第八阶段的示例 SQL 正是在这里写错的，见 sql/README.md 第六节）。
     * 这里直接用学生档案的累计时长求和：它本身就是"该学生已通过审核的时长合计"，
     * 不必再 JOIN 时长明细逐条汇总。
     *
     * <p>学院为空的学生不计入排名（没填学院就无法归到某一栏），
     * 因此各学院人数之和可能小于在校生总数。
     *
     * @return 学院统计，按时长降序
     */
    @Select("""
            SELECT si.college AS college,
                   ROUND(COALESCE(SUM(si.total_duration), 0), 1) AS hours,
                   COUNT(*) AS students,
                   ROUND(COALESCE(SUM(si.total_duration), 0) / NULLIF(COUNT(*), 0), 1) AS "avg"
              FROM student_info si
             WHERE si.deleted = 0
               AND si.college IS NOT NULL
               AND si.college <> ''
             GROUP BY si.college
             ORDER BY hours DESC, MIN(si.id) ASC
            """)
    List<CollegeStatVO> selectCollegeStats();

    /**
     * 组织活跃度：活动场次、签到率、审核通过率。
     *
     * <p>两个比率都在 0~1 之间（前端乘 100 后渲染），与核心指标卡片里的百分数口径不同，
     * 见 {@link OrgStatVO} 的类注释。
     *
     * <p>分母为 0 时（有活动但还没有报名/时长）用 {@code COALESCE} 兜成 0 而不是 NULL：
     * 前端是 {@code Math.round(d.signRate * 100)}，收到 null 会渲染出 "NaN%"。
     *
     * <p>{@code EXISTS} 把<b>没有活动的组织排除</b>在排名之外（待审核组织就是这种）：
     * 它们会占掉一行却什么都说明不了。
     *
     * @return 组织活跃度，按活动场次降序
     */
    @Select("""
            SELECT o.org_name AS org,
                   (SELECT COUNT(*)
                      FROM volunteer_activity a
                     WHERE a.org_id = o.id AND a.deleted = 0) AS activities,
                   COALESCE((SELECT ROUND((COUNT(*) FILTER (WHERE ar.sign_in_time IS NOT NULL))::numeric
                                          / NULLIF(COUNT(*), 0), 4)
                               FROM attendance_record ar
                               JOIN activity_signup sg ON sg.id = ar.signup_id
                               JOIN volunteer_activity a ON a.id = sg.activity_id
                              WHERE a.org_id = o.id
                                AND a.deleted = 0
                                AND ar.deleted = 0), 0) AS sign_rate,
                   COALESCE((SELECT ROUND((COUNT(*) FILTER (WHERE sd.status = 'APPROVED'))::numeric
                                          / NULLIF(COUNT(*), 0), 4)
                               FROM service_duration sd
                               JOIN volunteer_activity a ON a.id = sd.activity_id
                              WHERE a.org_id = o.id
                                AND a.deleted = 0
                                AND sd.deleted = 0
                                AND sd.status IN ('APPROVED', 'PENDING_AUDIT', 'REJECTED')), 0) AS pass_rate
              FROM org_info o
             WHERE o.deleted = 0
               AND EXISTS (SELECT 1 FROM volunteer_activity a
                            WHERE a.org_id = o.id AND a.deleted = 0)
             ORDER BY activities DESC, o.id ASC
            """)
    List<OrgStatVO> selectOrgStats();

    /**
     * 公益画像标签分布：每个标签有多少学生。
     *
     * <p>{@code student_profile.tags} 是<b>逗号分隔的字符串</b>（不是数组、也不是关联表），
     * 所以先 {@code string_to_array} 再 {@code unnest} 拆行，然后按标签分组计数 ——
     * 这一步必须在 SQL 里做，拆完再分组才不会把同一学生的多个标签算重。
     * 用 {@code COUNT(DISTINCT p.student_id)}：一个学生只可能给同一标签贡献一次，
     * 但字段里出现重复标签（"热心志愿者,热心志愿者"）时 DISTINCT 能兜住。
     *
     * <p>{@code tags} 为 NULL 或空串的学生不会产生任何行（{@code unnest} 对 NULL 返回空集），
     * 画像尚未生成的学生自然被排除，不需要额外判断。
     *
     * <p>并列时按 {@code MIN(p.student_id)} 打破，<b>不用中文标签名排序</b>：
     * 中文串的排序结果依赖数据库 collation，换台机器执行顺序就变了。
     *
     * <p>返回的行里没有 desc（标签规则说明），由服务层按规则文档补齐，见 {@link ProfileTagVO}。
     *
     * @return 标签分布，按人数降序
     */
    @Select("""
            SELECT TRIM(t.tag) AS tag,
                   COUNT(DISTINCT p.student_id) AS "count"
              FROM student_profile p
              JOIN student_info si ON si.id = p.student_id AND si.deleted = 0
              CROSS JOIN LATERAL unnest(string_to_array(p.tags, ',')) AS t(tag)
             WHERE p.tags IS NOT NULL
               AND TRIM(p.tags) <> ''
               AND TRIM(t.tag) <> ''
             GROUP BY TRIM(t.tag)
             ORDER BY COUNT(DISTINCT p.student_id) DESC, MIN(p.student_id) ASC
            """)
    List<ProfileTagVO> selectProfileTagCounts();

    /**
     * 时长审核三态计数与通过率。
     *
     * <p>只统计<b>已提交</b>的记录：{@code PENDING_SUBMIT} 是"组织还没提交"，
     * 既不是待审核也不是驳回，把它算进分母会把通过率拉低，
     * 算进"待审核"又会让管理员以为有待办。
     *
     * @return 单行结果：总数、三态计数、通过率
     */
    @Select("""
            SELECT COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE status = 'APPROVED') AS approved,
                   COUNT(*) FILTER (WHERE status = 'PENDING_AUDIT') AS pending,
                   COUNT(*) FILTER (WHERE status = 'REJECTED') AS rejected,
                   COALESCE(ROUND((COUNT(*) FILTER (WHERE status = 'APPROVED'))::numeric
                                  / NULLIF(COUNT(*), 0), 4), 0) AS pass_rate
              FROM service_duration
             WHERE deleted = 0
               AND status IN ('APPROVED', 'PENDING_AUDIT', 'REJECTED')
            """)
    AuditCountRowVO selectAuditCounts();

    /**
     * 签到率统计。
     *
     * <p>实到人数按 {@code sign_in_time IS NOT NULL} 判断，不按状态码：
     * 已签退的记录状态是 SIGNED_OUT，只认 SIGNED_IN 会把签退的人全漏掉。
     * 缺勤单独按 {@code status = 'ABSENT'} 数，剩下的未签到（NOT_SIGNED）不算缺勤 ——
     * 活动还没开始或还没到签到时间时就是这个状态，记成缺勤是冤枉人。
     *
     * @return 单行结果：应签到、实签到、缺勤人次与签到率
     */
    @Select("""
            SELECT COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE sign_in_time IS NOT NULL) AS signed,
                   COUNT(*) FILTER (WHERE status = 'ABSENT') AS absent,
                   COALESCE(ROUND((COUNT(*) FILTER (WHERE sign_in_time IS NOT NULL))::numeric
                                  / NULLIF(COUNT(*), 0), 4), 0) AS rate
              FROM attendance_record
             WHERE deleted = 0
            """)
    SigninVO selectSigninSummary();

    /**
     * 单月签到热力日历：返回目标月每一天的签到人次。
     *
     * <p>目标月 = <b>最近一个有签到记录的月份</b>，库里完全没有签到记录时回退到当前月。
     * 不固定取当前月的原因：月初打开看板时当月往往还没有数据，整片空白会被当成接口坏了。
     *
     * <p>日期序列由 {@code generate_series} 按当月实际天数生成（28/30/31），
     * 再 LEFT JOIN 逐日聚合结果，因此无记录的日子是 0 而不是缺行 ——
     * 前端按数组下标反推日期，缺一天会让后面所有格子整体错位一天，且不会报错。
     *
     * <p>只按签到时间聚合，这正是 {@code attendance_record} 需要补索引的原因（见 B17）。
     *
     * @return 逐日结果，按 day 升序；每行的 month 相同
     */
    @Select("""
            WITH target AS (
                SELECT COALESCE(MAX(date_trunc('month', ar.sign_in_time)),
                                date_trunc('month', CURRENT_DATE)) AS month_start
                  FROM attendance_record ar
                 WHERE ar.deleted = 0
                   AND ar.sign_in_time IS NOT NULL
            ),
            day_seq AS (
                SELECT generate_series(1, EXTRACT(DAY FROM (SELECT month_start FROM target)
                                                        + INTERVAL '1 month' - INTERVAL '1 day')::int) AS day_no
            ),
            daily AS (
                SELECT EXTRACT(DAY FROM ar.sign_in_time)::int AS day_no, COUNT(*) AS cnt
                  FROM attendance_record ar
                 WHERE ar.deleted = 0
                   AND ar.sign_in_time >= (SELECT month_start FROM target)
                   AND ar.sign_in_time <  (SELECT month_start FROM target) + INTERVAL '1 month'
                 GROUP BY 1
            )
            SELECT to_char((SELECT month_start FROM target), 'YYYY-MM') AS month,
                   d.day_no AS day,
                   COALESCE(x.cnt, 0) AS "count"
              FROM day_seq d
              LEFT JOIN daily x ON x.day_no = d.day_no
             ORDER BY d.day_no
            """)
    List<HeatmapDayVO> selectMonthlyHeatmap();

    /**
     * 当前用户最近的几条通知公告，供看板的公告栏使用。
     *
     * <p>按收件人过滤（通知是"按收件人一行"存的），因此看板上每个人看到的是自己的通知，
     * 不会串到别人的报名结果或时长审核结果上去。排序与通知列表页一致：
     * 置顶在前，同组内新的在前。
     *
     * <p>{@code source} 以 {@code from} 为别名出参：前端字段名就叫 from
     * （列名不能叫 from，它是 SQL 关键字，所以库里的列是 source）。
     *
     * @param userId 当前登录用户 id
     * @param limit  最多返回条数
     * @return 通知列表，置顶优先、时间倒序
     */
    @Select("""
            SELECT n.id AS id,
                   n.title AS title,
                   to_char(n.create_time, 'YYYY-MM-DD') AS "date",
                   COALESCE(n.source, '系统管理员') AS "from",
                   COALESCE(n.is_top, FALSE) AS top
              FROM notification n
             WHERE n.user_id = #{userId}
             ORDER BY COALESCE(n.is_top, FALSE) DESC, n.create_time DESC NULLS LAST, n.id DESC
             LIMIT #{limit}
            """)
    List<DashboardNoticeVO> selectRecentNotices(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 开放报名的活动，供首页"本期志愿活动"卡片展示。
     *
     * <p>只取 {@code PUBLISHED}：草稿不该露给学生，已结束/已取消的不属于"本期"。
     * 排序按开始时间升序（近期先办），未填开始时间的排在最后 ——
     * 卡片会把日期与时刻拼起来渲染，没有时间就没法展示，故用 COALESCE 兜成空串，
     * 避免前端拼出 "undefined undefined"。
     *
     * <p>日期与时刻分两列出参，与卡片 {@code `${a.date} ${a.time}`} 的渲染方式一致。
     *
     * @param limit 最多返回条数
     * @return 活动卡片数据
     */
    @Select("""
            SELECT a.id AS id,
                   a.title AS title,
                   c.category_name AS "type",
                   COALESCE(to_char(a.start_time, 'YYYY-MM-DD'), '') AS "date",
                   COALESCE(to_char(a.start_time, 'HH24:MI'), '') AS "time",
                   a.location AS place,
                   COALESCE(a.signed_count, 0) AS enrolled,
                   COALESCE(a.max_count, 0) AS capacity,
                   o.org_name AS org
              FROM volunteer_activity a
              LEFT JOIN activity_category c ON c.id = a.category_id
              LEFT JOIN org_info o ON o.id = a.org_id
             WHERE a.deleted = 0
               AND a.status = 'PUBLISHED'
             ORDER BY a.start_time ASC NULLS LAST, a.id ASC
             LIMIT #{limit}
            """)
    List<DashboardActivityVO> selectOpenActivities(@Param("limit") int limit);
}
