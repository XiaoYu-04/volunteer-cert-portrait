import { useUserStore } from '@/stores/user'

const APP_TITLE = '高校志愿服务时长认证与公益画像数据分析系统'

/**
 * 全局路由守卫。流程：
 *   无 token + 非公开页      → /login（带 redirect 回跳）
 *   有 token 无用户信息       → 拉取用户信息，失败则清登录态回登录页
 *   已登录访问登录/注册页     → 回本角色首页
 *   角色与路由 meta.roles 不符 → /403
 */
export function setupRouterGuard(router) {
  router.beforeEach(async (to) => {
    const user = useUserStore()

    if (!user.isLogin) {
      if (to.meta.public) return true
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    if (!user.info) {
      try {
        await user.fetchInfo()
      } catch {
        user.reset()
        return { path: '/login', query: { redirect: to.fullPath } }
      }
    }

    // 根路径按角色落地
    if (to.path === '/') return user.homePath

    if (to.path === '/login' || to.path === '/register') {
      return user.homePath
    }

    // 取路由链上第一个带 roles 的约束（父路由声明，子路由继承）
    const roles = to.matched.reduce((acc, record) => acc || record.meta?.roles, null)
    if (roles && !roles.includes(user.role)) {
      return '/403'
    }

    return true
  })

  router.afterEach((to) => {
    document.title = to.meta?.title ? `${to.meta.title} · ${APP_TITLE}` : APP_TITLE
  })
}