import ConsoleLayout from '@/layouts/ConsoleLayout.vue'

/** 组织端。侧边栏菜单由 meta.menu + meta.group 派生。 */
export default {
  path: '/org',
  component: ConsoleLayout,
  redirect: '/org/dashboard',
  meta: { roles: ['ORG_ADMIN'] },
  children: [
    {
      path: 'dashboard',
      name: 'OrgDashboard',
      component: () => import('@/views/org/OrgDashboardView.vue'),
      meta: { title: '组织数据', menu: true, group: '总览' },
    },
    {
      path: 'activities',
      name: 'OrgActivityManage',
      component: () => import('@/views/org/ActivityManageView.vue'),
      meta: { title: '活动管理', menu: true, group: '活动运营' },
    },
    {
      path: 'activities/new',
      name: 'OrgActivityPublish',
      component: () => import('@/views/org/ActivityPublishView.vue'),
      meta: { title: '发布活动', menu: true, group: '活动运营' },
    },
    {
      path: 'signups',
      name: 'OrgSignupAudit',
      component: () => import('@/views/org/SignupAuditView.vue'),
      meta: { title: '报名审核', menu: true, group: '活动运营' },
    },
    {
      path: 'attendance',
      name: 'OrgAttendance',
      component: () => import('@/views/org/AttendanceView.vue'),
      meta: { title: '签到管理', menu: true, group: '服务记录' },
    },
    {
      path: 'durations',
      name: 'OrgDurationSubmit',
      component: () => import('@/views/org/DurationSubmitView.vue'),
      meta: { title: '时长提交', menu: true, group: '服务记录' },
    },
  ],
}