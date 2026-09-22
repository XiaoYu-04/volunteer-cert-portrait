import { createRouter, createWebHistory } from 'vue-router'

import common from './routes/common'
import student from './routes/student'
import org from './routes/org'
import admin from './routes/admin'

/**
 * 顺序有讲究：通配的 404 路由在 common 里且排在最后，
 * 必须整体放在三个角色路由之后，否则会抢先匹配掉所有路径。
 */
const routes = [student, org, admin, ...common]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

export default router