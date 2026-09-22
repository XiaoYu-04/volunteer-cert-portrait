import ConsoleLayout from '@/layouts/ConsoleLayout.vue'

/** 学校端。 */
export default {
  path: '/admin',
  component: ConsoleLayout,
  redirect: '/admin/dashboard',
  meta: { roles: ['SCHOOL_ADMIN'] },
  children: [
    {
      path: 'dashboard',
      name: 'AdminDashboard',
      component: () => import('@/views/admin/DashboardView.vue'),
      meta: { title: '数据看板', menu: true, group: '总览' },
    },
    {
      path: 'durations',
      name: 'AdminDurationAudit',
      component: () => import('@/views/admin/DurationAuditView.vue'),
      meta: { title: '时长审核', menu: true, group: '认证管理' },
    },
    {
      path: 'portraits',
      name: 'AdminPortrait',
      component: () => import('@/views/admin/PortraitView.vue'),
      meta: { title: '公益画像', menu: true, group: '认证管理' },
    },
    {
      path: 'orgs',
      name: 'AdminOrgAudit',
      component: () => import('@/views/admin/OrgAuditView.vue'),
      meta: { title: '组织管理', menu: true, group: '认证管理' },
    },
    {
      path: 'categories',
      name: 'AdminCategories',
      component: () => import('@/views/admin/CategoryManageView.vue'),
      meta: { title: '分类管理', menu: true, group: '认证管理' },
    },
    {
      path: 'users',
      name: 'AdminUsers',
      component: () => import('@/views/admin/UserManageView.vue'),
      meta: { title: '用户管理', menu: true, group: '系统管理' },
    },
    {
      path: 'roles',
      name: 'AdminRoles',
      component: () => import('@/views/admin/RoleManageView.vue'),
      meta: { title: '角色管理', menu: true, group: '系统管理' },
    },
    {
      path: 'notices',
      name: 'AdminNotices',
      component: () => import('@/views/admin/NoticeManageView.vue'),
      meta: { title: '通知公告', menu: true, group: '系统管理' },
    },
    {
      path: 'logs',
      name: 'AdminLogs',
      component: () => import('@/views/admin/LogView.vue'),
      meta: { title: '操作日志', menu: true, group: '系统管理' },
    },
  ],
}