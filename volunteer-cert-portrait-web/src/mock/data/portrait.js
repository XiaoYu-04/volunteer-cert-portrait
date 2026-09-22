import { portraits, profiles, students, users } from './dataset'
import { ok, fail, paginate, like, eq, currentUserId } from './_helpers'

export default [
  /* 静态段必须排在 /v1/portraits/:id 之前（本模块暂无 :id 路由，保持约定） */
  {
    method: 'get',
    path: '/v1/portraits/me',
    handler: ({ headers }) => {
      const uid = currentUserId(headers)
      const user = users.find((u) => u.id === uid)
      const studentId = user?.studentId || 1
      const mine = portraits.find((p) => p.studentId === studentId)
      if (!mine) return fail(50001, '画像尚未生成')

      const student = students.find((s) => s.id === studentId)
      return ok({
        ...mine,
        major: student?.major || '',
        grade: student?.grade || '',
        // 画像构成：用于雷达/柱状展示
        dimensions: [
          { name: '服务时长', value: Math.min(100, Math.round((mine.totalHours / 50) * 100)) },
          { name: '活动场次', value: Math.min(100, Math.round((mine.activityCount / 15) * 100)) },
          { name: '社区服务', value: Math.round(mine.communityRatio * 100) },
          { name: '环保行动', value: Math.round(mine.environmentRatio * 100) },
          { name: '持续性', value: 78 },
        ],
      })
    },
  },
  {
    method: 'get',
    path: '/v1/portraits/distribution',
    handler: () => ok(profiles),
  },
  {
    method: 'get',
    path: '/v1/portraits',
    handler: ({ query }) => {
      const rows = portraits
        .filter((p) => like(p.studentName, query.keyword) || like(p.studentNo, query.keyword))
        .filter((p) => eq(p.college, query.college))
        .filter((p) => eq(p.tag, query.tag))
      return ok(paginate(rows, query))
    },
  },
  {
    method: 'get',
    path: '/v1/portraits/:studentId',
    handler: ({ params }) => {
      const item = portraits.find((p) => p.studentId === Number(params.studentId))
      return item ? ok(item) : fail(50001, '画像尚未生成')
    },
  },
]