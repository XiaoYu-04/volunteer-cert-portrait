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

import authRoutes from './data/auth'
import systemRoutes from './data/system'
import orgRoutes from './data/org'
import volunteerRoutes from './data/volunteer'
import certificationRoutes from './data/certification'
import portraitRoutes from './data/portrait'
import analyticsRoutes from './data/analytics'

const routes = [
  ...authRoutes,
  ...systemRoutes,
  ...orgRoutes,
  ...volunteerRoutes,
  ...certificationRoutes,
  ...portraitRoutes,
  ...analyticsRoutes,
]

import { ok, fail } from './data/_helpers'

// 响应构造器定义在 data/_helpers.js，这里转出，方便调用方从一处引入
export { ok, fail }

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