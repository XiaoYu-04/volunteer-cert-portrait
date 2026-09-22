/**
 * 接口请求统一入口。
 *
 * 按 VITE_USE_MOCK 分流：true 走 src/mock 的本地模拟，false 走真实 axios。
 * 两条路径最终都汇到同一个 unwrap()，因此页面拿到的永远是一致的
 * 「成功即 resolve 业务数据、失败即 reject ApiError」的语义。
 */

import axios from 'axios'
import { getToken, removeToken } from './auth'
import { mockRequest } from '@/mock'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

/** 认证失败的错误码，命中即清 token 并跳登录 */
const UNAUTHORIZED_CODES = [20001, 20002, 20003]

export class ApiError extends Error {
  constructor(code, message) {
    super(message || '请求失败')
    this.name = 'ApiError'
    this.code = code
  }
}

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 统一解包 { code, message, data }。
 * 成功返回 data，失败抛 ApiError，认证类失败额外清理登录态。
 */
function unwrap(response) {
  const body = response || {}
  const code = Number(body.code)

  if (code === 0) {
    return body.data
  }

  if (UNAUTHORIZED_CODES.includes(code)) {
    removeToken()
    // 用 replace 避免在历史记录里堆积受保护页面
    const { pathname, search, hash } = window.location
    const from = encodeURIComponent(pathname + search + hash)
    if (!pathname.startsWith('/login')) {
      window.location.replace(`/login?redirect=${from}`)
    }
  }

  throw new ApiError(code, body.message)
}

/**
 * @param {{url:string, method?:string, params?:object, data?:any}} config
 * @returns {Promise<any>} 成功时 resolve 业务数据（已剥掉 code/message 外壳）
 */
export function request(config) {
  if (USE_MOCK) {
    // mock 不走 axios 拦截器，这里手动把 Authorization 带上，
    // 让 mock 侧能像真实后端一样识别当前登录用户
    const token = getToken()
    return mockRequest({
      ...config,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    }).then(unwrap)
  }

  return service(config)
    .then((res) => unwrap(res.data))
    .catch((err) => {
      // HTTP 401 也要清登录态，与业务码路径保持一致
      if (err.response && err.response.status === 401) {
        removeToken()
        const { pathname, search, hash } = window.location
        const from = encodeURIComponent(pathname + search + hash)
        if (!pathname.startsWith('/login')) {
          window.location.replace(`/login?redirect=${from}`)
        }
        throw new ApiError(20001, '登录已过期，请重新登录')
      }
      if (err instanceof ApiError) throw err
      throw new ApiError(10000, err.message || '网络异常，请稍后重试')
    })
}

export const get = (url, params) => request({ url, method: 'get', params })
export const post = (url, data) => request({ url, method: 'post', data })
export const put = (url, data) => request({ url, method: 'put', data })
export const del = (url, data) => request({ url, method: 'delete', data })

export default request