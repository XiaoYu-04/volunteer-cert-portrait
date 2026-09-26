/**
 * 登录态与权限。
 *
 * 角色编码与架构文档一致：STUDENT / ORG_ADMIN / SCHOOL_ADMIN。
 * 权限标识统一为「域:资源:操作」，与后端 Sa-Token 的鉴权串保持同构，
 * 因此前端静态定义一份即可，后端接上后无需改动页面。
 */
import { defineStore } from 'pinia'
import { login as apiLogin, logout as apiLogout, getCurrentUser } from '@/api/auth'
import { getToken, setToken, removeToken } from '@/utils/auth'

/** 角色 → 权限标识 */
export const ROLE_PERMS = {
  STUDENT: [
    'volunteer:activity:list',
    'volunteer:signup:create',
    'volunteer:signup:cancel',
    'certification:duration:mine',
    'portrait:profile:mine',
  ],
  ORG_ADMIN: [
    'volunteer:activity:list',
    'volunteer:activity:create',
    'volunteer:activity:update',
    'volunteer:activity:publish',
    'volunteer:signup:audit',
    'volunteer:attendance:manage',
    'certification:duration:submit',
  ],
  SCHOOL_ADMIN: [
    'volunteer:activity:list',
    'volunteer:activity:update',
    'volunteer:signup:audit',
    'volunteer:attendance:manage',
    'volunteer:category:manage',
    'org:info:audit',
    'certification:duration:approve',
    'portrait:profile:list',
    'system:user:list',
    'system:user:create',
    'system:user:update',
    'system:user:delete',
    'system:role:list',
    'system:college:manage',
    'system:notice:manage',
    'system:log:list',
    'analytics:dashboard:view',
  ],
}

/** 角色 → 登录后的默认落地页 */
export const ROLE_HOME = {
  STUDENT: '/student',
  ORG_ADMIN: '/org',
  SCHOOL_ADMIN: '/admin',
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    /** { id, username, name, role, roleLabel, orgId, studentId, college, studentNo, phone, email, avatarText } */
    info: null,
  }),

  getters: {
    isLogin: (state) => !!state.token,
    role: (state) => state.info?.role || '',
    name: (state) => state.info?.name || '',
    roleLabel: (state) => state.info?.roleLabel || '',
    homePath: (state) => ROLE_HOME[state.info?.role] || '/login',
    perms: (state) => ROLE_PERMS[state.info?.role] || [],
  },

  actions: {
    /**
     * 写入会话。state 与 localStorage 必须一起写 ——
     * 只改 state 的话当前会话内看着正常，一刷新 token 就没了。
     */
    applySession({ token, user }) {
      this.token = token
      this.info = user
      setToken(token)
      return user
    },

    async login(payload) {
      return this.applySession(await apiLogin(payload))
    },

    /** 刷新页面后用 token 换回用户信息 */
    async fetchInfo() {
      const info = await getCurrentUser()
      this.info = info
      return info
    },

    async logout() {
      try {
        await apiLogout()
      } catch {
        // 退出接口失败也要清本地登录态，否则用户会卡在登录页进不去
      }
      this.reset()
    },

    reset() {
      this.token = ''
      this.info = null
      removeToken()
    },

    /** 按钮级权限判断，配合 v-perm 指令使用 */
    hasPerm(perm) {
      if (!perm) return true
      return this.perms.includes(perm)
    },
  },
})
