/**
 * ECharts 配置工厂。
 *
 * 自原型 styles_12-chinese-ink.html 的 ODCharts 逐条移植，做了两处改造：
 *   1. 每个图表从「读全局 D.xxx」改成「接收 data 参数的纯函数」
 *   2. 主题令牌仍通过 getComputedStyle 从 :root 读取，
 *      保证 CSS 是配色的唯一数据源，图表不会与页面脱节
 *
 * 移植时保留了原型的全部细节，包括柱状图的 borderRadius —— 页面 UI 是零圆角，
 * 但图表内元素保留轻微圆角，这是原型的刻意选择，不要「统一」掉。
 */

/* ---------- 令牌读取 ---------- */

function tok(name, fallback) {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name)
  return (v || '').trim() || fallback
}

let cache = null

function theme() {
  if (cache) return cache
  cache = {
    panel: tok('--c-panel', '#ffffff'),
    line: tok('--c-line', '#DCD8D0'),
    ink: tok('--c-ink', '#1A1A1A'),
    ink2: tok('--c-ink-2', '#4A4642'),
    ink3: tok('--c-ink-3', '#6B6560'),
    // 图表统一走无衬线栈，而标题与卡片标签走衬线栈 —— 这个区分是原型有意为之
    font: tok('--font-body', 'sans-serif'),
    ok: tok('--c-ok', '#3E6B4A'),
    warn: tok('--c-warn', '#A8894A'),
    bad: tok('--c-bad', '#B03A2E'),
    // 浮层阴影与页面同一令牌：图表提示框不能比弹窗还重
    shadowPop: tok('--shadow-pop', '0 10px 30px rgba(26,26,26,.12)'),
    series: [
      tok('--c-a1', '#1A1A1A'),
      tok('--c-a2', '#B03A2E'),
      tok('--c-a3', '#3E6B7A'),
      tok('--c-a4', '#A8894A'),
      tok('--c-a5', '#6B6560'),
      tok('--c-a6', '#C4BEB4'),
    ],
  }
  return cache
}

/** 把颜色转成带透明度的 rgba，支持 #rgb / #rrggbb / rgb() */
function alpha(color, a) {
  const c = (color || '').trim()
  if (c.charAt(0) === '#') {
    let hx = c.slice(1)
    if (hx.length === 3) hx = hx[0] + hx[0] + hx[1] + hx[1] + hx[2] + hx[2]
    const n = parseInt(hx, 16)
    if (Number.isNaN(n)) return c
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`
  }
  if (c.indexOf('rgb(') === 0) return c.replace('rgb(', 'rgba(').replace(')', `,${a})`)
  return c
}

/** 保留 1 位小数后加千分位，与原型 D.fmt 一致 */
export function fmt(n) {
  const rounded = Math.round(Number(n) * 10) / 10
  const [int, dec] = String(rounded).split('.')
  const withSep = int.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return dec ? `${withSep}.${dec}` : withSep
}

/* ---------- 公共片段 ---------- */

const reduced = () =>
  !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches)

function base() {
  const t = theme()
  return {
    animation: !reduced(),
    animationDuration: 600,
    animationEasing: 'cubicOut',
    textStyle: { fontFamily: t.font, color: t.ink2 },
    tooltip: {
      backgroundColor: t.panel,
      borderColor: t.line,
      borderWidth: 1,
      padding: [8, 12],
      textStyle: { color: t.ink, fontFamily: t.font, fontSize: 12 },
      // 零圆角是页面语言；提示框跟随，不单独做圆角与发光
      extraCssText: `border-radius:0;box-shadow:${t.shadowPop};`,
    },
  }
}

function catAxis(data) {
  const t = theme()
  return {
    type: 'category',
    data,
    boundaryGap: false,
    axisLine: { lineStyle: { color: t.line } },
    axisTick: { show: false },
    axisLabel: { color: t.ink3, fontSize: 11 },
  }
}

function valAxis(name) {
  const t = theme()
  return {
    type: 'value',
    name: name || '',
    nameTextStyle: { color: t.ink3, fontSize: 11, padding: [0, 0, 0, -18] },
    splitLine: { lineStyle: { color: t.line, type: 'dashed' } },
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: t.ink3, fontSize: 11 },
  }
}

function legend() {
  const t = theme()
  return {
    bottom: 0,
    icon: 'circle',
    itemWidth: 9,
    itemHeight: 9,
    itemGap: 14,
    textStyle: { color: t.ink2, fontSize: 11, fontFamily: t.font },
  }
}

/**
 * 环形 / 玫瑰图的 emphasis 态。
 *
 * 只做轻微放大，数值交给 tooltip —— **不再画外侧强调标签**：
 * 环图一旦占满大半高度，顶部 / 底部扇区的外侧标签就会超出画布、被上/下边缘裁掉
 * （2026-09-26 实测：学校端「审核三态」环图 hover「已驳回」，标签只剩半行）。
 * tooltip 不受画布裁剪，且字段更全（名称 + 数值 + 占比），悬停信息统一走 tooltip。
 */
function pieEmphasis() {
  return { scale: true, scaleSize: 6 }
}

const monthLabel = (month) => `${Number(month.slice(5))}月`

/* ---------- 图表工厂 ---------- */

/** 活动数量趋势：面积折线 */
export function trend(data) {
  const t = theme()
  const o = base()
  o.grid = { left: 4, right: 16, top: 26, bottom: 4, containLabel: true }
  o.tooltip = Object.assign({}, o.tooltip, {
    trigger: 'axis',
    formatter: (p) => {
      const d = data[p[0].dataIndex]
      return `${d.month}<br/>活动 <b>${d.count}</b> 场<br/>服务 <b>${fmt(d.hours)}</b> 小时`
    },
  })
  o.xAxis = catAxis(data.map((d) => monthLabel(d.month)))
  o.yAxis = valAxis('场')
  o.series = [
    {
      type: 'line',
      smooth: true,
      showSymbol: false,
      symbol: 'circle',
      symbolSize: 6,
      data: data.map((d) => d.count),
      lineStyle: { width: 2, color: t.series[0] },
      itemStyle: { color: t.series[0] },
      emphasis: { focus: 'series' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: alpha(t.series[0], 0.34) },
            { offset: 1, color: alpha(t.series[0], 0) },
          ],
        },
      },
    },
  ]
  return o
}

/** 服务时长趋势：折线（无面积） */
export function hours(data) {
  const t = theme()
  const o = base()
  o.grid = { left: 4, right: 16, top: 26, bottom: 4, containLabel: true }
  o.tooltip = Object.assign({}, o.tooltip, {
    trigger: 'axis',
    formatter: (p) => {
      const d = data[p[0].dataIndex]
      return `${d.month}<br/>服务 <b>${fmt(d.hours)}</b> 小时`
    },
  })
  o.xAxis = catAxis(data.map((d) => monthLabel(d.month)))
  o.yAxis = valAxis('小时')
  o.series = [
    {
      type: 'line',
      smooth: false,
      showSymbol: true,
      symbolSize: 5,
      data: data.map((d) => d.hours),
      lineStyle: { width: 2, color: t.series[1] },
      itemStyle: { color: t.series[1] },
    },
  ]
  return o
}

/** 活动类型占比：环形 */
export function typePie(data) {
  const t = theme()
  const o = base()
  o.tooltip = Object.assign({}, o.tooltip, {
    trigger: 'item',
    formatter: '{b}<br/>{c} 场（{d}%）',
  })
  o.legend = legend()
  o.series = [
    {
      type: 'pie',
      radius: ['50%', '70%'],
      center: ['50%', '43%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: t.panel, borderWidth: 2 },
      label: { show: false },
      labelLine: { show: false },
      emphasis: pieEmphasis(),
      data: data.map((d, i) => ({
        name: d.name,
        value: d.value,
        itemStyle: { color: t.series[i % 6] },
      })),
    },
  ]
  return o
}

/** 学院志愿时长排名：横向条形（按值升序，ECharts y 轴自下而上） */
export function college(data) {
  const t = theme()
  const rows = data.slice().sort((a, b) => a.hours - b.hours)
  const o = base()
  o.grid = { left: 4, right: 58, top: 10, bottom: 4, containLabel: true }
  o.tooltip = Object.assign({}, o.tooltip, {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    formatter: (p) => `${p[0].name}<br/>志愿时长 <b>${fmt(p[0].value)}</b> 小时`,
  })
  o.xAxis = valAxis('')
  o.yAxis = {
    type: 'category',
    data: rows.map((d) => d.college),
    axisLine: { lineStyle: { color: t.line } },
    axisTick: { show: false },
    axisLabel: { color: t.ink2, fontSize: 12 },
  }
  o.series = [
    {
      type: 'bar',
      barWidth: '56%',
      data: rows.map((d, i) => ({
        value: d.hours,
        itemStyle: {
          // 由浅入深的墨色渐变，最高的一条最深
          color: alpha(t.series[0], 0.42 + 0.58 * (i / Math.max(1, rows.length - 1))),
          borderRadius: [0, 4, 4, 0],
        },
      })),
      label: {
        show: true,
        position: 'right',
        color: t.ink2,
        fontSize: 11,
        formatter: (p) => fmt(p.value),
      },
    },
  ]
  return o
}

/**
 * 组织名在两种数据形态下字段不同：
 * dataset.orgs（聚合）用 org，dataset.orgList（实体）用 name。
 * 这里两种都认，避免调用方为了喂图表而改写数据。
 */
const orgName = (d) => d.org ?? d.name ?? ''

/** 组织活跃度：横向条形 */
export function orgBar(data) {
  const t = theme()
  const rows = data.slice().sort((a, b) => a.activities - b.activities)
  const o = base()
  o.grid = { left: 4, right: 42, top: 10, bottom: 4, containLabel: true }
  o.tooltip = Object.assign({}, o.tooltip, {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    formatter: (p) => {
      const d = rows[p[0].dataIndex]
      return `${orgName(d)}<br/>活动 <b>${d.activities}</b> 场<br/>签到率 ${Math.round(d.signRate * 100)}%<br/>审核通过率 ${Math.round(d.passRate * 100)}%`
    },
  })
  o.xAxis = valAxis('场')
  o.yAxis = {
    type: 'category',
    data: rows.map(orgName),
    axisLine: { lineStyle: { color: t.line } },
    axisTick: { show: false },
    axisLabel: { color: t.ink2, fontSize: 12 },
  }
  o.series = [
    {
      type: 'bar',
      barWidth: '52%',
      data: rows.map((d) => ({
        value: d.activities,
        itemStyle: { color: t.series[1], borderRadius: [0, 4, 4, 0] },
      })),
      label: { show: true, position: 'right', color: t.ink2, fontSize: 11 },
    },
  ]
  return o
}

/**
 * 单个组织的活跃度雷达
 * @param {object} org
 * @param {{ activityMax?: number }} [opts] 「活动场次」轴的量程上限，默认 40。
 *   调用方应按全校组织的最大活动数传入；**量程必须不小于数据**，否则 ECharts 不做截断，
 *   三角形顶点会冲出外圈、被画布上缘裁掉（2026-09-26 实测：某组织 68 场 vs 量程 40）。
 */
export function orgRadar(org, { activityMax = 40 } = {}) {
  const t = theme()
  const o = base()
  o.tooltip = Object.assign({}, o.tooltip, {})
  o.radar = {
    // 三角形尖朝上：外接圆圆心在画布正中时，三角形的视觉中心比圆心高 r/4。
    // 规则：圆心纵向下移 r/4 才能让三角形居中 —— radius 每 +1%，center 纵向约 +0.125%。
    // 当前 radius 78%（r≈109px）时 r/4≈27px，即 10% → center 纵向取 60%。
    center: ['50%', '60%'],
    radius: '78%',
    indicator: [
      { name: '活动场次', max: activityMax },
      { name: '签到率 %', max: 100 },
      { name: '审核通过率 %', max: 100 },
    ],
    axisName: { color: t.ink2, fontSize: 11 },
    splitLine: { lineStyle: { color: t.line } },
    splitArea: { areaStyle: { color: [alpha(t.series[0], 0.05), 'transparent'] } },
    axisLine: { lineStyle: { color: t.line } },
  }
  o.series = [
    {
      type: 'radar',
      symbolSize: 5,
      data: [
        {
          name: orgName(org),
          value: [org.activities, Math.round(org.signRate * 100), Math.round(org.passRate * 100)],
          lineStyle: { color: t.series[0], width: 2 },
          itemStyle: { color: t.series[0] },
          areaStyle: { color: alpha(t.series[0], 0.26) },
        },
      ],
    },
  ]
  return o
}

/** 学生公益画像分布：玫瑰图（rose=false 时退化为环形） */
export function profile(data, { rose = true } = {}) {
  const t = theme()
  const o = base()
  o.tooltip = Object.assign({}, o.tooltip, {
    trigger: 'item',
    formatter: '{b}<br/>{c} 人（{d}%）',
  })
  o.legend = legend()
  o.series = [
    {
      type: 'pie',
      radius: rose ? [26, 112] : ['48%', '70%'],
      center: ['50%', '43%'],
      roseType: rose ? 'radius' : false,
      itemStyle: { borderColor: t.panel, borderWidth: 2, borderRadius: rose ? 4 : 0 },
      label: { show: false },
      labelLine: { show: false },
      emphasis: pieEmphasis(),
      data: data.map((d, i) => ({
        name: d.tag,
        value: d.count,
        itemStyle: { color: t.series[i % 6] },
      })),
    },
  ]
  return o
}

/**
 * 学生公益画像分布：柱状。
 * yName 可覆盖 Y 轴单位——画画像分布时是「人」，画单个学生的维度得分时是「分」。
 */
export function profileBar(data, { yName = '人' } = {}) {
  const t = theme()
  const o = base()
  o.grid = { left: 4, right: 12, top: 22, bottom: 4, containLabel: true }
  o.tooltip = Object.assign({}, o.tooltip, { trigger: 'axis', axisPointer: { type: 'shadow' } })
  o.xAxis = Object.assign(catAxis(data.map((d) => d.tag)), { boundaryGap: true })
  o.yAxis = valAxis(yName)
  o.series = [
    {
      type: 'bar',
      barWidth: '50%',
      data: data.map((d, i) => ({
        value: d.count,
        itemStyle: { color: t.series[i % 6], borderRadius: [4, 4, 0, 0] },
      })),
      label: {
        show: true,
        position: 'top',
        color: t.ink2,
        fontSize: 11,
        formatter: (p) => fmt(p.value),
      },
    },
  ]
  return o
}

/** 时长审核三态：细环形。颜色走 ok/warn/bad 三态语义色，不用系列色 */
export function audit(items) {
  const t = theme()
  const tone = { ok: t.ok, warn: t.warn, bad: t.bad }
  const o = base()
  o.tooltip = Object.assign({}, o.tooltip, { trigger: 'item', formatter: '{b}<br/>{c} 条（{d}%）' })
  o.legend = legend()
  o.series = [
    {
      type: 'pie',
      // 环形居中：底部图例（legend bottom:0）占了约 30px，圆心不能取 50%，
      // 取 46% 让环形在「画布顶部 ↔ 图例顶部」之间上下留白相等。
      // radius 同步放大到 84%（细环比例不变，内径 62%），比原 76% 更饱满。
      radius: ['62%', '84%'],
      center: ['50%', '46%'],
      itemStyle: { borderColor: t.panel, borderWidth: 2 },
      label: { show: false },
      labelLine: { show: false },
      emphasis: pieEmphasis(),
      data: items.map((d) => ({
        name: d.name,
        value: d.value,
        itemStyle: { color: tone[d.tone] },
      })),
    },
  ]
  return o
}

/** 签到率：仪表盘 */
export function sign({ rate }) {
  const t = theme()
  const o = base()
  // 仪表盘默认 tooltip 只显示裸数值（如 "90.1"，还带一个无意义的系列色点）；
  // 这里补上名称与百分号，和其余图表的 tooltip 口径一致。
  o.tooltip = Object.assign({}, o.tooltip, {
    formatter: (p) => `活动签到率<br/><b>${p.value}%</b>`,
  })
  o.series = [
    {
      type: 'gauge',
      startAngle: 210,
      endAngle: -30,
      min: 0,
      max: 100,
      radius: '88%',
      center: ['50%', '56%'],
      progress: {
        show: true,
        width: 12,
        roundCap: true,
        itemStyle: { color: t.series[0] },
      },
      axisLine: { lineStyle: { width: 12, color: [[1, alpha(t.series[0], 0.16)]] } },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { show: false },
      pointer: { show: false },
      anchor: { show: false },
      title: { show: false },
      detail: {
        valueAnimation: !reduced(),
        fontSize: 26,
        fontWeight: 700,
        fontFamily: t.font,
        color: t.ink,
        offsetCenter: [0, '4%'],
        formatter: '{value}%',
      },
      data: [{ value: +(rate * 100).toFixed(1) }],
    },
  ]
  return o
}

/**
 * 单月签到热力日历。
 * @param {{month:string, days:number[]}} data  month 形如 '2025-03'，days 为当月逐日值
 */
export function heat(data) {
  const t = theme()
  const max = Math.max.apply(null, data.days)
  const o = base()
  o.tooltip = Object.assign({}, o.tooltip, {
    formatter: (p) => `${p.value[0]}<br/>服务 <b>${p.value[1]}</b> 人次`,
  })
  o.visualMap = {
    min: 0,
    max,
    show: false,
    inRange: {
      color: [alpha(t.series[0], 0.1), alpha(t.series[0], 0.48), t.series[0]],
    },
  }
  o.calendar = {
    range: data.month,
    // 原型里这个图在窄容器中，cellSize 高度 17 就够；这里放在整宽面板里，
    // 用百分比约束左右留白并加大行高，否则单元格会被拉成细长的横条
    cellSize: ['auto', 30],
    left: '16%',
    right: '16%',
    top: 40,
    bottom: 8,
    itemStyle: { color: 'transparent', borderColor: t.line, borderWidth: 1 },
    splitLine: { show: false },
    dayLabel: { color: t.ink3, fontSize: 10, nameMap: 'ZH' },
    monthLabel: { color: t.ink2, fontSize: 11, nameMap: 'ZH' },
    yearLabel: { show: false },
  }
  o.series = [
    {
      type: 'heatmap',
      coordinateSystem: 'calendar',
      data: heatmapSeries(data),
    },
  ]
  return o
}

/** 把 days 数组转成 calendar 需要的 [['2025-03-01', 88], ...] */
function heatmapSeries({ month, days }) {
  return days.map((v, i) => [`${month}-${String(i + 1).padStart(2, '0')}`, v])
}