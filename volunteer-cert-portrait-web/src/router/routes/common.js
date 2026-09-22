/**
 * 公共路由：登录、注册、错误页。
 * 全部使用空白布局，不套学生端/控制台外壳。
 */
export default [
  {
    // 落地页由守卫按角色改写，这里只给一个兜底
    path: '/',
    name: 'Root',
    redirect: '/login',
    meta: { public: true },
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/RegisterView.vue'),
    meta: { title: '注册', public: true },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: { title: '无权访问', public: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { title: '页面不存在', public: true },
  },
]