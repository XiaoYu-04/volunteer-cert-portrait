import StudentLayout from '@/layouts/StudentLayout.vue'

/**
 * 学生端。meta.menu 决定是否进顶部导航，meta.group 是导航分组名。
 */
export default {
  path: '/student',
  component: StudentLayout,
  redirect: '/student/home',
  meta: { roles: ['STUDENT'] },
  children: [
    {
      path: 'home',
      name: 'StudentHome',
      component: () => import('@/views/student/HomeView.vue'),
      meta: { title: '首页', menu: true, group: '总览' },
    },
    {
      path: 'activities',
      name: 'ActivityList',
      component: () => import('@/views/student/ActivityListView.vue'),
      meta: { title: '志愿活动', menu: true, group: '志愿活动' },
    },
    {
      path: 'activities/:id',
      name: 'ActivityDetail',
      component: () => import('@/views/student/ActivityDetailView.vue'),
      meta: { title: '活动详情', activeMenu: '/student/activities' },
    },
    {
      path: 'signups',
      name: 'MySignups',
      component: () => import('@/views/student/MySignupsView.vue'),
      meta: { title: '我的报名', menu: true, group: '我的' },
    },
    {
      path: 'durations',
      name: 'MyDurations',
      component: () => import('@/views/student/MyDurationsView.vue'),
      meta: { title: '我的时长', menu: true, group: '我的' },
    },
    {
      path: 'portrait',
      name: 'MyPortrait',
      component: () => import('@/views/student/MyPortraitView.vue'),
      meta: { title: '我的公益画像', menu: true, group: '我的' },
    },
    {
      path: 'notifications',
      name: 'StudentNotifications',
      component: () => import('@/views/student/NotificationsView.vue'),
      meta: { title: '通知公告', menu: true, group: '我的' },
    },
    {
      path: 'notifications/:id',
      name: 'NotificationDetail',
      component: () => import('@/views/student/NotificationDetailView.vue'),
      meta: { title: '通知详情', activeMenu: '/student/notifications' },
    },
    {
      path: 'profile',
      name: 'StudentProfile',
      component: () => import('@/views/student/ProfileView.vue'),
      meta: { title: '个人中心', menu: true, group: '我的' },
    },
  ],
}