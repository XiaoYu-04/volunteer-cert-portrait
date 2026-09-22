import { activities, categories, signups, attendance, students, orgList } from './dataset'
import { ok, fail, paginate, like, eq, nextId, now } from './_helpers'

/**
 * 组织隔离：报名与签到记录本身不带 orgId，需要经 activityId 反查活动归属。
 * 真实后端应在 SQL 里 join，这里用等价的查找代替。
 */
function belongsToOrg(row, orgId) {
  if (!orgId) return true
  const activity = activities.find((a) => a.id === row.activityId)
  return activity ? activity.orgId === Number(orgId) : false
}

export default [
  /* ---------- 组织端概览 ----------
     必须排在 /v1/activities/:id 之前，否则会被当成 id='org-overview' 抢先匹配 */
  {
    method: 'get',
    path: '/v1/activities/org-overview',
    handler: ({ query }) => {
      const orgId = Number(query.orgId) || 1
      const org = orgList.find((o) => o.id === orgId) || orgList[0]
      const mine = activities.filter((a) => a.orgId === orgId)
      const pending = signups.filter(
        (s) => s.status === 'PENDING' && mine.some((a) => a.id === s.activityId),
      ).length
      const unsigned = attendance.filter((a) => a.status === 'NOT_SIGNED' || a.status === 'ABSENT').length
      return ok({
        org,
        stats: [
          { key: 'activities', label: '累计活动', value: org.activities, unit: '场', delta: 8.0, trend: 'up' },
          { key: 'ongoing', label: '进行中活动', value: mine.filter((a) => a.status === 'PUBLISHED').length, unit: '场', delta: 4.2, trend: 'up' },
          { key: 'pendingSignup', label: '待审报名', value: pending, unit: '人', delta: -6.5, trend: 'down' },
          { key: 'signRate', label: '签到率', value: +(org.signRate * 100).toFixed(1), unit: '%', delta: 1.1, trend: 'up' },
          { key: 'passRate', label: '审核通过率', value: +(org.passRate * 100).toFixed(1), unit: '%', delta: 0.6, trend: 'up' },
          { key: 'unsigned', label: '未签到人次', value: unsigned, unit: '人次', delta: -3.4, trend: 'down' },
        ],
      })
    },
  },

  /* ---------- 活动 ---------- */
  {
    method: 'get',
    path: '/v1/activities',
    handler: ({ query }) => {
      let rows = activities
        .filter((a) => like(a.title, query.keyword))
        .filter((a) => eq(a.type, query.type))
        .filter((a) => eq(a.status, query.status))
        .filter((a) => eq(a.orgId, query.orgId))
      if (query.onlyOpen === 'true' || query.onlyOpen === true) {
        rows = rows.filter((a) => a.status === 'PUBLISHED' && a.enrolled < a.capacity)
      }
      return ok(paginate(rows, query))
    },
  },
  {
    method: 'get',
    path: '/v1/activities/:id',
    handler: ({ params }) => {
      const activity = activities.find((a) => a.id === Number(params.id))
      if (!activity) return fail(30001, '活动不存在')
      return ok(activity)
    },
  },
  {
    method: 'post',
    path: '/v1/activities',
    handler: ({ body }) => {
      const id = nextId(activities)
      activities.unshift({
        id,
        title: body.title,
        type: body.type,
        date: body.date,
        time: body.time,
        place: body.place,
        enrolled: 0,
        capacity: Number(body.capacity) || 0,
        org: body.org || '青年志愿者协会',
        orgId: Number(body.orgId) || 1,
        hours: Number(body.hours) || 0,
        status: 'DRAFT',
        categoryId: Number(body.categoryId) || 1,
        deadline: body.deadline || '',
        contact: body.contact || '',
        description: body.description || '',
      })
      return ok({ id }, '活动已创建为草稿')
    },
  },
  {
    method: 'put',
    path: '/v1/activities/:id',
    handler: ({ params, body }) => {
      const activity = activities.find((a) => a.id === Number(params.id))
      if (!activity) return fail(30001, '活动不存在')
      Object.assign(activity, body, { id: activity.id })
      return ok(null, '保存成功')
    },
  },
  {
    method: 'put',
    path: '/v1/activities/:id/status',
    handler: ({ params, body }) => {
      const activity = activities.find((a) => a.id === Number(params.id))
      if (!activity) return fail(30001, '活动不存在')
      activity.status = body.status
      const label = { DRAFT: '已转为草稿', PUBLISHED: '已发布', CLOSED: '已结束', CANCELED: '已取消' }
      return ok(null, label[body.status] || '状态已更新')
    },
  },

  /* ---------- 活动分类 ---------- */
  {
    method: 'get',
    path: '/v1/categories',
    handler: () => ok(categories),
  },
  {
    method: 'post',
    path: '/v1/categories',
    handler: ({ body }) => {
      if (categories.some((c) => c.name === body.name)) {
        return fail(30002, '分类名称已存在')
      }
      categories.push({
        id: nextId(categories),
        name: body.name,
        code: body.code || `CUSTOM_${categories.length + 1}`,
        activityCount: 0,
        sort: Number(body.sort) || categories.length + 1,
        status: 'ACTIVE',
        remark: body.remark || '',
      })
      return ok(null, '新增成功')
    },
  },
  {
    method: 'put',
    path: '/v1/categories/:id',
    handler: ({ params, body }) => {
      const item = categories.find((c) => c.id === Number(params.id))
      if (!item) return fail(30003, '分类不存在')
      Object.assign(item, body, { id: item.id, activityCount: item.activityCount })
      return ok(null, '保存成功')
    },
  },
  {
    method: 'delete',
    path: '/v1/categories/:id',
    handler: ({ params }) => {
      const item = categories.find((c) => c.id === Number(params.id))
      if (!item) return fail(30003, '分类不存在')
      if (item.activityCount > 0) {
        return fail(30004, `该分类下还有 ${item.activityCount} 场活动，无法删除`)
      }
      categories.splice(categories.indexOf(item), 1)
      return ok(null, '已删除')
    },
  },

  /* ---------- 报名 ---------- */
  {
    method: 'get',
    path: '/v1/signups',
    handler: ({ query }) => {
      const rows = signups
        .filter((s) => like(s.studentName, query.keyword) || like(s.activityTitle, query.keyword))
        .filter((s) => eq(s.status, query.status))
        .filter((s) => eq(s.activityId, query.activityId))
        .filter((s) => eq(s.studentId, query.studentId))
        .filter((s) => eq(s.college, query.college))
        .filter((s) => belongsToOrg(s, query.orgId))
      return ok(paginate(rows, query))
    },
  },
  {
    method: 'post',
    path: '/v1/signups',
    handler: ({ body }) => {
      const activity = activities.find((a) => a.id === Number(body.activityId))
      if (!activity) return fail(30001, '活动不存在')
      if (activity.status !== 'PUBLISHED') return fail(30005, '该活动当前不接受报名')
      if (activity.enrolled >= activity.capacity) return fail(30006, '名额已满')

      const student = students.find((s) => s.id === Number(body.studentId)) || students[0]
      if (signups.some((s) => s.activityId === activity.id && s.studentId === student.id && s.status !== 'CANCELED')) {
        return fail(30007, '你已报名该活动，请勿重复提交')
      }

      const id = nextId(signups)
      signups.unshift({
        id,
        activityId: activity.id,
        activityTitle: activity.title,
        activityDate: activity.date,
        activityHours: activity.hours,
        studentId: student.id,
        studentName: student.name,
        studentNo: student.studentNo,
        college: student.college,
        status: 'PENDING',
        appliedAt: now(),
        reason: body.reason || '',
        rejectReason: '',
      })
      activity.enrolled += 1
      return ok({ id }, '报名已提交，等待组织审核')
    },
  },
  {
    method: 'put',
    path: '/v1/signups/:id/audit',
    handler: ({ params, body }) => {
      const item = signups.find((s) => s.id === Number(params.id))
      if (!item) return fail(30008, '报名记录不存在')
      if (item.status !== 'PENDING') return fail(30009, '该报名已审核，无法重复操作')

      item.status = body.action === 'APPROVE' ? 'APPROVED' : 'REJECTED'
      item.rejectReason = body.action === 'APPROVE' ? '' : body.remark || '不符合本次活动要求'

      if (body.action === 'REJECT') {
        const activity = activities.find((a) => a.id === item.activityId)
        if (activity && activity.enrolled > 0) activity.enrolled -= 1
      }
      return ok(null, body.action === 'APPROVE' ? '已通过' : '已驳回')
    },
  },
  {
    method: 'put',
    path: '/v1/signups/:id/cancel',
    handler: ({ params }) => {
      const item = signups.find((s) => s.id === Number(params.id))
      if (!item) return fail(30008, '报名记录不存在')
      if (item.status === 'COMPLETED') return fail(30010, '已完成的活动无法取消')
      item.status = 'CANCELED'
      const activity = activities.find((a) => a.id === item.activityId)
      if (activity && activity.enrolled > 0) activity.enrolled -= 1
      return ok(null, '已取消报名')
    },
  },

  /* ---------- 签到 ---------- */
  {
    method: 'get',
    path: '/v1/attendance',
    handler: ({ query }) => {
      const rows = attendance
        .filter((a) => like(a.studentName, query.keyword) || like(a.activityTitle, query.keyword))
        .filter((a) => eq(a.status, query.status))
        .filter((a) => eq(a.activityId, query.activityId))
        .filter((a) => eq(a.studentId, query.studentId))
        .filter((a) => belongsToOrg(a, query.orgId))
      return ok(paginate(rows, query))
    },
  },
  {
    method: 'put',
    path: '/v1/attendance/:id',
    handler: ({ params, body }) => {
      const item = attendance.find((a) => a.id === Number(params.id))
      if (!item) return fail(30011, '签到记录不存在')
      item.status = body.status ?? item.status
      if (body.signInAt !== undefined) item.signInAt = body.signInAt
      if (body.signOutAt !== undefined) item.signOutAt = body.signOutAt
      item.hours = item.status === 'SIGNED_OUT' ? item.hours || 4 : 0
      return ok(null, '已更新')
    },
  },
  {
    method: 'post',
    path: '/v1/attendance/sign-in',
    handler: ({ body }) => {
      const item = attendance.find((a) => a.id === Number(body.attendanceId))
      if (!item) return fail(30011, '签到记录不存在')
      if (item.status !== 'NOT_SIGNED' && item.status !== 'ABSENT') {
        return fail(30012, '当前状态无法签到')
      }
      item.status = 'SIGNED_IN'
      item.signInAt = now()
      return ok(null, '签到成功')
    },
  },
  {
    method: 'post',
    path: '/v1/attendance/sign-out',
    handler: ({ body }) => {
      const item = attendance.find((a) => a.id === Number(body.attendanceId))
      if (!item) return fail(30011, '签到记录不存在')
      if (item.status !== 'SIGNED_IN') return fail(30013, '请先签到再签退')
      item.status = 'SIGNED_OUT'
      item.signOutAt = now()
      item.hours = item.hours || 4
      return ok(null, '签退成功，服务时长已记录')
    },
  },

  ]