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
]