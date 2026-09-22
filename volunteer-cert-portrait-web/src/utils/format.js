/**
 * 展示层格式化工具。
 * 约定：数字类输出统一走千分位，时间类统一走 pad 补零。
 */

const pad = (n) => String(n).padStart(2, '0')

/** 千分位：86420 → "86,420" */
export function formatNumber(value) {
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  if (Number.isNaN(n)) return String(value)
  return n.toLocaleString('zh-CN')
}

/** 日期：'2025-03-22T09:00:00' → '2025-03-22' */
export function formatDate(value) {
  if (!value) return '—'
  const d = toDate(value)
  if (!d) return String(value)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 日期时间：'2025-03-22 09:00' */
export function formatDateTime(value, withSeconds = false) {
  if (!value) return '—'
  const d = toDate(value)
  if (!d) return String(value)
  const base = `${formatDate(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  return withSeconds ? `${base}:${pad(d.getSeconds())}` : base
}

/** 相对时间：'3 天前'。超过 30 天回退成绝对日期 */
export function formatRelative(value) {
  if (!value) return '—'
  const d = toDate(value)
  if (!d) return String(value)
  const diff = Date.now() - d.getTime()
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 30) return `${day} 天前`
  return formatDate(d)
}

/** 百分比：0.923 → '92.3%'。入参为 0~1 的小数 */
export function formatPercent(ratio, digits = 1) {
  if (ratio === null || ratio === undefined || ratio === '') return '—'
  const n = Number(ratio)
  if (Number.isNaN(n)) return String(ratio)
  return `${(n * 100).toFixed(digits)}%`
}

/** 时长：4.5 → '4.5 小时'；整数省略小数位 */
export function formatHours(value) {
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  if (Number.isNaN(n)) return String(value)
  return `${Number.isInteger(n) ? n : n.toFixed(1)} 小时`
}

/** 取姓名末两字作印章文字，用于 .seal */
export function sealText(name) {
  if (!name) return '志愿'
  const s = String(name).trim()
  return s.length <= 2 ? s : s.slice(-2)
}

function toDate(value) {
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? null : d
}