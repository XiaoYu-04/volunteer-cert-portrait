/**
 * Mock 分发层。
 *
 * 设计意图：api/ 下的接口函数一律按真实后端契约书写，由 utils/request.js
 * 按 VITE_USE_MOCK 决定走这里还是走真实 axios。后端就绪后把 .env 里的开关
 * 翻成 false 即可切换，页面代码一行不用改。
 *
 * 响应结构严格遵循架构文档约定：{ code, message, data }，成功 code=0；
 * 分页统一 { total, list }。错误码分段：
 *   1xxxx 通用 / 2xxxx 认证权限 / 3xxxx 活动报名 / 4xxxx 时长认证 / 5xxxx 画像统计
 */

import authRoutes from './data/auth.js'
import systemRoutes from './data/system.js'
import orgRoutes from './data/org.js'
import volunteerRoutes from './data/volunteer.js'
import certificationRoutes from './data/certification.js'
import portraitRoutes from './data/portrait.js'
import analyticsRoutes from './data/analytics.js'

const routes = [
  ...authRoutes,
  ...systemRoutes,
  ...orgRoutes,
  ...volunteerRoutes,
  ...certificationRoutes,
  ...portraitRoutes,
  ...analyticsRoutes,
]

import { fail, checkSession } from './data/_helpers.js'

/**
 * 免登录路径，与后端 `SaTokenConfig.EXCLUDE_PATHS` 逐项对齐。
 * 登录 / 注册 / 学院下拉本身不能要求先登录，否则永远拿不到第一个 token。
 */
const AUTH_FREE_PATHS = ['/v1/auth/login', '/v1/auth/register', '/v1/auth/colleges']

/**
 * 路径匹配，支持 :param 占位。
 * '/v1/activities/:id' 匹配 '/v1/activities/12' → { id: '12' }
 */
function matchPath(pattern, path) {
  const p = pattern.split('/').filter(Boolean)
  const a = path.split('/').filter(Boolean)
  if (p.length !== a.length) return null
  const params = {}
  for (let i = 0; i < p.length; i++) {
    if (p[i].charAt(0) === ':') {
      params[p[i].slice(1)] = decodeURIComponent(a[i])
    } else if (p[i] !== a[i]) {
      return null
    }
  }
  return params
}

/** 模拟网络延迟，让加载态在开发时真的能被看到 */
function delay() {
  return new Promise((resolve) => setTimeout(resolve, 180 + Math.random() * 220))
}

/**
 * @param {{url:string, method:string, params?:object, data?:any}} config
 * @returns {Promise<{code:number, message:string, data:any}>}
 */
export async function mockRequest(config) {
  const { url = '', method = 'get', params = {}, data, headers = {} } = config
  const path = url.split('?')[0]
  const verb = String(method).toLowerCase()

  /* 会话检查，排在路由匹配之前 —— 对齐后端：SaInterceptor 覆盖 /**、先于 Controller 执行，
     所以「失效 token + 不存在的接口」在后端拿到的是 20001 而不是 10007，这里也一样。 */
  if (!AUTH_FREE_PATHS.includes(path)) {
    const session = checkSession(headers)
    if (session === 'replaced' || session === 'invalid') {
      await delay()
      return fail(
        20001,
        session === 'replaced' ? '账号已在其它地方登录，当前会话已失效' : '登录已过期，请重新登录',
      )
    }
  }

  for (const route of routes) {
    if (route.method !== verb) continue
    const pathParams = matchPath(route.path, path)
    if (!pathParams) continue

    await delay()

    let body = data
    if (typeof body === 'string') {
      try {
        body = JSON.parse(body)
      } catch {
        body = {}
      }
    }

    return route.handler({ params: pathParams, query: params || {}, body: body || {}, headers })
  }

  return fail(10404, `Mock 未定义该接口：${verb.toUpperCase()} ${path}`)
}