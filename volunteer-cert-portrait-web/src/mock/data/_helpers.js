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

/* ============================================================
   会话注册表：一个账号只保留最新一个 token
   ============================================================
   对齐后端 sa-token 的 `is-concurrent: false`（见 application.yml）：
   同账号第二次登录会把先登录那台的 token 标记失效，旧 token 再请求拿到 20001。
   后端由 Sa-Token 维护终端列表，这里用一张「userId → 最新 token」的表等价实现，
   拦截点在 mock/index.js 的分发层 —— 与后端把校验放在 SaInterceptor 而非各 Controller
   是同一个位置关系。

   落 localStorage 而不是留在内存：整页刷新会重建模块，纯内存的话刷新一次表就空了，
   而 localStorage 里那个 token 还在 —— 用户会被自己的 token「顶下线」，
   表现成「mock 模式下刷新页面必被踢回登录页」。存下来后刷新仍是同一会话，
   真正的顶下线只发生在另一个标签页登录同一账号时。
   跨标签页共享 localStorage 也正是这里想要的：另一个标签页登录才作数。
   ============================================================ */

const SESSIONS_KEY = 'vcp_mock_sessions'

/** 解出请求头里的裸 token（去掉 'Bearer ' 前缀） */
function bearerToken(headers = {}) {
  return String(headers.Authorization || headers.authorization || '')
    .replace(/^Bearer\s+/i, '')
    .trim()
}

function loadSessions() {
  try {
    const raw = localStorage.getItem(SESSIONS_KEY)
    const map = raw ? JSON.parse(raw) : null
    return map && typeof map === 'object' ? map : {}
  } catch {
    // localStorage 不可用（隐私模式、被禁用）时退回空表：新token照发，只是不顶旧token
    return {}
  }
}

function saveSessions(map) {
  try {
    localStorage.setItem(SESSIONS_KEY, JSON.stringify(map))
  } catch {
    // 写不进去不影响本次会话
  }
}

/**
 * 签发新 token 并把该账号此前的 token 作废。
 * token 形如 'mock-token-3-k2m9x1'，带上随机后缀是为了让两次登录拿到不同的值 ——
 * 否则「后登录顶掉先登录」在同一个浏览器里根本看不出来（两边 token 一样）。
 */
export function issueToken(userId) {
  const token = `mock-token-${userId}-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
  const sessions = loadSessions()
  sessions[String(userId)] = token
  saveSessions(sessions)
  return token
}

/** 主动登出：删掉该账号的记录，登出后旧 token 再请求会被 checkSession 判为失效 */
export function revokeToken(headers = {}) {
  const token = bearerToken(headers)
  const sessions = loadSessions()
  for (const [userId, current] of Object.entries(sessions)) {
    if (current === token) delete sessions[userId]
  }
  saveSessions(sessions)
}

/**
 * 会话状态检查，返回 'ok' | 'replaced' | 'invalid' | 'unknown'。
 * 由 mock/index.js 的分发层统一前置调用 —— 位置对应后端的 SaInterceptor。
 *
 * 四种取值与后端的对应关系：
 *   ok       当前账号记的就是这个 token            → 放行
 *   replaced 记的是另一个 token（同账号又登录了）  → 后端 NotLoginException.BE_REPLACED
 *   invalid  token 认得出、但表里没有这个账号      → 后端 INVALID_TOKEN（登出 / 记录被清）
 *   unknown  token 压根不像 mock 发的（含没带）    → 不表态，交给 handler 照旧走
 *
 * invalid 与 replaced 分开是为了文案：前者是「登录过期」，后者才是「别处登录」，
 * 后端也是两个分支（GlobalExceptionHandler 只对 BE_REPLACED 换文案）。
 * unknown 不在这里拦，是为了保持改动前的行为 —— 无 token 时各 handler
 * 本来就返回 20001「登录已过期」，不该被顶下线这条新逻辑改变。
 */
export function checkSession(headers = {}) {
  const token = bearerToken(headers)
  const m = token.match(/^mock-token-(\d+)-/)
  if (!m) return 'unknown'
  const current = loadSessions()[m[1]]
  if (current === undefined) return 'invalid'
  return current === token ? 'ok' : 'replaced'
}

/**
 * 从 Authorization 头里解出当前用户 id。
 * token 形如 'mock-token-3-k2m9x1'，与 issueToken 的签发逻辑对应；
 * 是否已被顶下线不在这里判 —— 那是 mock/index.js 分发层的统一前置检查。
 */
export function currentUserId(headers = {}) {
  const m = bearerToken(headers).match(/^mock-token-(\d+)-/)
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

/* ============================================================
   注册账号的跨刷新持久化
   ============================================================
   mock 数据活在内存里，整页刷新会重置模块。注册产生的新账号如果不落盘，
   就会出现「注册 → 刷新 → 被踢回登录页，且新账号再也登不回来」。

   只持久化账号这一项：其余实体（活动、报名、时长…）保持内存态，
   演示时刷新即回到干净的种子数据，可以反复走同一条流程。
   ============================================================ */

const USERS_KEY = 'vcp_mock_extra_users'

export function loadPersistedUsers(seed) {
  try {
    const raw = localStorage.getItem(USERS_KEY)
    const extra = raw ? JSON.parse(raw) : []
    return Array.isArray(extra) && extra.length ? [...seed, ...extra] : seed
  } catch {
    // localStorage 不可用（或 Node 里跑校验脚本）时退回种子数据
    return seed
  }
}

export function persistUser(user) {
  try {
    const raw = localStorage.getItem(USERS_KEY)
    const extra = raw ? JSON.parse(raw) : []
    extra.push(user)
    localStorage.setItem(USERS_KEY, JSON.stringify(extra))
  } catch {
    // 存储写不进去不影响本次会话
  }
}