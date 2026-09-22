/**
 * mock 路由的共享工具。
 * 单独成文件是为了避免 data/* 反向 import mock/index.js 造成循环依赖。
 */

/** 成功响应 */
export function ok(data = null, message = 'ok') {
  return { code: 0, message, data }
}

/** 失败响应，code 取错误码分段：1xxxx 通用 / 2xxxx 认证权限 / 3xxxx 活动报名 / 4xxxx 时长认证 / 5xxxx 画像统计 */
export function fail(code, message) {
  return { code, message, data: null }
}

/** 分页，返回架构文档约定的 { total, list } */
export function paginate(rows, query = {}) {
  const page = Math.max(1, Number(query.page) || 1)
  const pageSize = Math.max(1, Number(query.pageSize) || 10)
  const start = (page - 1) * pageSize
  return { total: rows.length, list: rows.slice(start, start + pageSize) }
}

/** 关键字模糊匹配，忽略大小写，空关键字直接放行 */
export function like(value, keyword) {
  if (!keyword) return true
  return String(value || '')
    .toLowerCase()
    .includes(String(keyword).toLowerCase())
}

/** 相等匹配，条件为空时放行 */
export function eq(value, expected) {
  if (expected === undefined || expected === null || expected === '') return true
  return String(value) === String(expected)
}

/**
 * 从 Authorization 头里解出当前用户 id。
 * token 形如 'mock-token-3'，与 auth.js 的签发逻辑对应。
 */
export function currentUserId(headers = {}) {
  const raw = headers.Authorization || headers.authorization || ''
  const m = String(raw).match(/mock-token-(\d+)/)
  return m ? Number(m[1]) : null
}

/** 生成下一个 id */
export function nextId(rows) {
  return rows.reduce((max, row) => Math.max(max, Number(row.id) || 0), 0) + 1
}

/** 当前时间戳字符串，形如 '2025-03-21 09:12:33' */
export function now() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}