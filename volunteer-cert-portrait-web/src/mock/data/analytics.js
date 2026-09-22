import {
  stats,
  trend,
  types,
  colleges,
  orgs,
  profiles,
  audit,
  signin,
  heatmap,
  flow,
  notices,
  activities,
} from './dataset'
import { ok } from './_helpers'

export default [
  /* 首页/看板一次拉全，避免首屏串行发七八个请求 */
  {
    method: 'get',
    path: '/v1/analytics/dashboard',
    handler: () =>
      ok({
        stats,
        trend,
        types,
        colleges,
        orgs,
        profiles,
        audit,
        signin,
        heatmap,
        flow,
        notices,
        // 首页的「本期志愿活动」只展示开放报名的，取前 6 条由前端切
        activities: activities.filter((a) => a.status === 'PUBLISHED'),
      }),
  },
  {
    method: 'get',
    path: '/v1/analytics/overview',
    handler: () => ok(stats),
  },
  {
    method: 'get',
    path: '/v1/analytics/trend',
    handler: () => ok(trend),
  },
  {
    method: 'get',
    path: '/v1/analytics/types',
    handler: () => ok(types),
  },
  {
    method: 'get',
    path: '/v1/analytics/colleges',
    handler: () => ok(colleges),
  },
  {
    method: 'get',
    path: '/v1/analytics/orgs',
    handler: () => ok(orgs),
  },
  {
    method: 'get',
    path: '/v1/analytics/profiles',
    handler: () => ok(profiles),
  },
  {
    method: 'get',
    path: '/v1/analytics/audit',
    handler: () => ok(audit),
  },
  {
    method: 'get',
    path: '/v1/analytics/signin',
    handler: () => ok(signin),
  },
  {
    method: 'get',
    path: '/v1/analytics/heatmap',
    handler: () => ok(heatmap),
  },
]