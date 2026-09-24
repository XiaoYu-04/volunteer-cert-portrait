import { computed } from 'vue'
import { formatNumber, formatPercent } from '@/utils/format'

/**
 * 看板文案：把 `GET /v1/analytics/dashboard` 的响应转成图注与无障碍描述。
 *
 *   const { trendLabel, collegeDesc } = useDashboardText(data)
 *
 * 为什么要集中在这里：这些位置原先写的是原型（mock）的冻结数字 ——
 * 386 场、86,420 小时、「计算机学院 15240 小时居首，体育学院 7980 小时最少」——
 * 接上真实接口后，图里画的是真数据、旁边的文字还在断言 mock 的数，同一屏自相矛盾；
 * `aria-label` 更是读屏用户唯一能听到的内容，写死等于给视障用户报假数。
 *
 * 做法照 admin/PortraitView.vue 的 `distributionLabel`（B20-4 留下的范例）：
 * 一律从接口数据现算，不出现字面数字。
 *
 * 另注：后端配了 default-property-inclusion=non_null，值为 null 的字段会**整键消失**，
 * 因此这里一律可选链 + 兜底，不要假设某个键一定存在。
 *
 * @param {import('vue').Ref<object|null>} data dashboard 响应
 */
export function useDashboardText(data) {
  const stats = computed(() => data.value?.stats || [])
  const trend = computed(() => data.value?.trend || [])
  const types = computed(() => data.value?.types || [])
  const colleges = computed(() => data.value?.colleges || [])
  const auditItems = computed(() => data.value?.audit?.items || [])
  const profiles = computed(() => data.value?.profiles || [])
  const signin = computed(() => data.value?.signin || {})

  /** '2026-08' → '2026 年 8 月' */
  const monthText = (ym) => (ym ? `${String(ym).slice(0, 4)} 年 ${Number(String(ym).slice(5))} 月` : '')

  /** 取活动量最高/最低的月份；并列取先出现者，与图表顺序一致 */
  function trendExtremes(rows) {
    if (!rows.length) return { peak: null, low: null }
    let peak = rows[0]
    let low = rows[0]
    for (const row of rows) {
      if ((row.count ?? 0) > (peak.count ?? 0)) peak = row
      if ((row.count ?? 0) < (low.count ?? 0)) low = row
    }
    return { peak, low }
  }

  const extremes = computed(() => trendExtremes(trend.value))

  /** 首屏 kicker：用趋势数据的真实年月区间，替代写死的「2025 · 春季学期」 */
  const trendRange = computed(() => {
    const rows = trend.value
    if (!rows.length) return ''
    return `${monthText(rows[0].month)} – ${monthText(rows[rows.length - 1].month)}`
  })

  /** 逐月活动量合计。刻意不用 stats.activities —— 那是全量活动数，
      而 trend 只覆盖近 12 个月（下月及以后的活动被窗口截掉），两者本就不等，
      图注必须与图同口径，否则图上 14 条、文字写 20 场。 */
  const trendTotal = computed(() => trend.value.reduce((sum, r) => sum + (r.count || 0), 0))
  const trendHours = computed(() => trend.value.reduce((sum, r) => sum + (r.hours || 0), 0))
  /** 图注 / 面板角标用的合计：加载态给 null，formatNumber 会渲染成「—」。
      直接写 0 会被读成真实数据（「近 12 个月合计 0 场」）。 */
  const trendTotalText = computed(() => (trend.value.length ? trendTotal.value : null))
  const trendHoursText = computed(() => (trend.value.length ? trendHours.value : null))

  const trendDesc = computed(() => {
    const { peak, low } = extremes.value
    if (!peak || !low) return '各月活动量分布'
    return `近 12 个月以 ${monthText(peak.month)}最高（${peak.count} 场），${monthText(low.month)}最低（${low.count} 场）。`
  })

  const trendLabel = computed(() => {
    const { peak, low } = extremes.value
    if (!peak || !low) return '逐月活动数量折线图'
    return `逐月活动数量折线图：${monthText(peak.month)} ${peak.count} 场最高，${monthText(low.month)} ${low.count} 场最低`
  })

  const hoursLabel = computed(() => {
    const { peak, low } = extremes.value
    if (!peak || !low) return '逐月服务时长折线图'
    return `逐月服务时长折线图：${monthText(peak.month)} ${peak.hours} 小时，${monthText(low.month)} ${low.hours} 小时`
  })

  /** 活动类型：按场次降序取占比最高的那类 */
  const topType = computed(() =>
    types.value.length ? types.value.reduce((a, b) => ((b.value ?? 0) > (a.value ?? 0) ? b : a)) : null,
  )

  const typeLabel = computed(() =>
    types.value.length
      ? `活动类型占比环形图：${types.value.map((t) => `${t.name} ${t.value} 场`).join('、')}`
      : '活动类型占比环形图',
  )

  const typeCaption = computed(() =>
    topType.value ? `${topType.value.name} ${topType.value.value} 场占比最高` : '按分类统计',
  )

  /** 学院时长：接口已按 hours 降序，但 aria 要的是「最高/最少」，现算更稳 */
  const collegeTop = computed(() => (colleges.value.length ? colleges.value[0] : null))
  const collegeLow = computed(() =>
    colleges.value.length ? colleges.value.reduce((a, b) => ((b.hours ?? 0) < (a.hours ?? 0) ? b : a)) : null,
  )

  const collegeLabel = computed(() => {
    const top = collegeTop.value
    const low = collegeLow.value
    if (!top || !low) return '各学院志愿时长排名横向条形图'
    return `各学院志愿时长排名横向条形图，${top.college} ${top.hours} 小时居首，${low.college} ${low.hours} 小时最少`
  })

  const collegeDesc = computed(() => {
    const top = collegeTop.value
    if (!top) return '按累计认证时长排序'
    return `按累计认证时长排序，${top.college}以 ${formatNumber(top.hours)} 小时居首。`
  })

  const auditLabel = computed(() => {
    if (!auditItems.value.length) return '时长审核三态环形图'
    const parts = auditItems.value.map((i) => `${i.name} ${i.value} 条`).join('、')
    return `时长审核三态环形图：${parts}，通过率 ${formatPercent(data.value?.audit?.passRate)}`
  })

  const auditDesc = computed(() => {
    if (!auditItems.value.length) return '志愿服务时长审核三态分布（条）'
    const parts = auditItems.value.map((i) => `${i.name} ${formatNumber(i.value)} 条`).join('、')
    return `志愿服务时长审核三态分布（条），${parts}。`
  })

  const signinLabel = computed(() => {
    const s = signin.value
    if (s.total === undefined) return '签到率仪表盘'
    return `签到率仪表盘：${formatPercent(s.rate)}，应签到 ${s.total} 人次，实签到 ${s.signed} 人次`
  })

  const signinDesc = computed(() => {
    const s = signin.value
    if (s.total === undefined) return '活动签到率。'
    return `活动签到率，应签到 ${formatNumber(s.total)} 人次、实签到 ${formatNumber(s.signed)} 人次。`
  })

  /** 画像区文案。刻意不写「全校 N 名学生」——profiles 是「标签 → 人数」的分布，
      一个学生可同时有多个标签，其 count 之和是**标签计数**而非学生数，写成学生数会偏大。 */
  const portraitDesc = computed(
    () => `共 ${profiles.value.length} 类画像标签，按参与活动的类型、频次与时长归类。`,
  )

  /** 「N 类活动 · M 个学院」这类结构性说明 */
  const structureNote = computed(() => {
    const parts = []
    if (types.value.length) parts.push(`${types.value.length} 类活动`)
    if (colleges.value.length) parts.push(`${colleges.value.length} 个学院`)
    return parts.join(' · ')
  })

  const heatLabel = computed(() => {
    const month = data.value?.heatmap?.month
    if (!month) return '逐日签到人次热力日历'
    return `${monthText(month)}逐日签到人次热力日历`
  })

  return {
    // 原始切片，页面直接用
    stats,
    trend,
    types,
    colleges,
    auditItems,
    profiles,
    signin,
    topType,
    collegeTop,
    collegeLow,
    // 现算文案
    trendRange,
    trendTotal,
    trendHours,
    trendTotalText,
    trendHoursText,
    trendDesc,
    trendLabel,
    hoursLabel,
    typeLabel,
    typeCaption,
    collegeLabel,
    collegeDesc,
    auditLabel,
    auditDesc,
    signinLabel,
    signinDesc,
    portraitDesc,
    structureNote,
    heatLabel,
    monthText,
  }
}
