/**
 * Token 存取。集中一处，便于后端改认证方案时只动这个文件。
 */
const TOKEN_KEY = 'vcp_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}