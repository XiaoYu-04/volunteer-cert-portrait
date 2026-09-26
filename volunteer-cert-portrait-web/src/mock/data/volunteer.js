import { activities, categories, signups, attendance, students, orgList, users } from './dataset'
import { ok, fail, paginate, like, eq, nextId, now, currentUserId } from './_helpers'

/**
 * 组织隔离：报名与签到记录本身不带 orgId，需要经 activityId 反查活动归属。
 * 真实后端应在 SQL 里 join，这里用等价的查找代替。
 */
function belongsToOrg(row, orgId) {
  if (!orgId) return true
  const activity = activities.find((a) => a.id === row.activityId)
  return activity ? activity.orgId === Number(orgId) : false
}

/** 活动「日期 + 开始时间 + 时长」推出结束时间；与后端 endTimeOf(start, hours) 同口径 */
function activityEndAt(activity) {
  const d = new Date(`${activity.date}T${activity.time}:00`)
  d.setHours(d.getHours() + Number(activity.hours || 0))
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:00`
}

/**
 * 签到记录 → 学生端 VO（待办 B24）。
 *
 * canSignIn / canSignOut 在真后端是由 AttendancePolicy 按 A2 的 30 分钟窗口算好的。
 * mock **刻意不做窗口校验**（只看状态）：演示数据里的活动都在 2025 年，按窗口算的话
 * 签到按钮永远点不了，「纯前端独立演示」（VITE_USE_MOCK=true，答辩用）就走不通闭环。
 * 连带的差异：mock 里点签到不会出现真后端那句「签到尚未开放，活动开始前 30 分钟才可签到」。
 */
function toMyAttendance(row) {
  const activity = activities.find((a) => a.id === row.activityId)
  return {
    ...row,
    activityStartAt: activity ? `${activity.date} ${activity.time}:00` : '',
    activityEndAt: activity ? activityEndAt(activity) : '',
    canSignIn: row.status === 'NOT_SIGNED' || row.status === 'ABSENT',
    canSignOut: row.status === 'SIGNED_IN',
  }
}

/** 解析 'yyyy-MM-dd HH:mm[:ss]'（签到时间与人工修正输入框都是这个形态），解析不出返回 null */
function parseAt(text) {
  const m = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?$/.exec(String(text || '').trim())
  return m ? new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +(m[6] || 0)) : null
}

/** 在 'yyyy-MM-dd HH:mm:ss' 上叠加小时数；解析不出来时返回空串（调用方自行兜底） */
function plusHours(text, hours) {
  const d = parseAt(text)
  if (!d) return ''
  d.setHours(d.getHours() + Number(hours || 0))
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/**
 * 实得时长（小时，2 位小数），与后端 AttendancePolicy 同口径：
 * 已签退且有完整签到签退时间时按 (签退 − 签到) / 3600 算，封顶活动预计时长的 1.5 倍；
 * 其余状态一律 0。
 *
 * 三处入口（人工修正 / 签到 / 签退）共用这一个函数：原先签退写 `item.hours || 4`、
 * 人工修正写 `item.hours || 4`，都会造出「显示 4 小时、两个时间却只差几秒」——
 * 正是种子数据当初修掉的 C14 那类矛盾，只是换到了运行期。
 */
function resolveMockHours(item) {
  if (item.status !== 'SIGNED_OUT') return 0
  const signIn = parseAt(item.signInAt)
  const signOut = parseAt(item.signOutAt)
  if (!signIn || !signOut) return 0
  const actual = (signOut - signIn) / 3600000
  const planned = Number(activities.find((a) => a.id === item.activityId)?.hours) || 0
  const capped = planned > 0 ? Math.min(actual, planned * 1.5) : actual
  return Math.max(0, Math.round(capped * 100) / 100)
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
      // 与真后端对齐：取消报名会连带作废该报名的签到记录
      // （AttendanceRecordMapper.invalidateBySignupId 把 deleted 置 1，它就不再出现在
      //  签到列表 / 我的签到 / 统计口径里）。mock 没有 deleted 列，从数组里摘掉即等价。
      // 不摘的话「我的报名」会出现「已取消 + 未签到」这种真后端不可能出现的组合（B24 新增了签到列）。
      const idx = attendance.findIndex((a) => a.signupId === item.id)
      if (idx >= 0) attendance.splice(idx, 1)
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
  /* 学生本人的签到记录（待办 B24）。
     静态段必须排在 /v1/attendance/:id 之前（CLAUDE.md 前端踩坑第 4 条），
     否则 'mine' 会被当成 id 抢先匹配 —— 现在两者 method 不同（这里是 get、那条是 put）
     侥幸不会撞，但只要日后有人给 :id 补一个 get，顺序错了就会静默走错 handler。 */
  {
    method: 'get',
    path: '/v1/attendance/mine',
    handler: ({ query, headers }) => {
      const user = users.find((u) => u.id === currentUserId(headers))
      // 非学生账号（组织管理员 / 学校管理员）走这里。
      // 错误码刻意用 10003（通用参数类）而不是 20003：20003 在 utils/request.js 的
      // UNAUTHORIZED_CODES 里，命中会清 token 并跳登录页 —— 管理员点进来被踢下线，
      // 而真后端同样是 10003（与 system.js 的「学生档案不存在」同一个码）。
      if (!user || !user.studentId) {
        return fail(10003, '当前账号未关联学生档案，请联系学校管理员')
      }
      // 数据范围只看登录态：**刻意忽略 query.studentId 与 query.orgId**。
      // 这不是漏写 —— 真后端 GET /attendance/mine 同样忽略这两个参数（传了也无效），
      // 学生不应该能通过改 URL 参数看到别人的签到记录。别"顺手补全"。
      const rows = attendance
        .filter((a) => a.studentId === user.studentId)
        .filter((a) => eq(a.status, query.status))
        .filter((a) => eq(a.activityId, query.activityId))
        .filter((a) => like(a.activityTitle, query.keyword))
        .map(toMyAttendance)
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
      // 时间与状态都改完之后按统一口径重算实得时长。
      // 原先是 `item.status === 'SIGNED_OUT' ? item.hours || 4 : 0`：管理员把时间改正了、
      // 时长却仍是旧值或凭空 4 小时，属于 C14 的同一类矛盾（现在被签到入口点得到）。
      item.hours = resolveMockHours(item)
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
      item.hours = 0
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
      // 签退时刻由「签到时刻 + 活动时长」推出，不取 now()：现场点完签到几秒后就点签退，
      // 若写 now() 再记满额时长，就复现了种子数据当初修掉的 C14 矛盾（时间差几秒却记 4 小时）。
      // 真后端写的是真实 now()，所以那边秒级签退得到 0.00 小时 —— 这是 mock 与真后端
      // 在这一处的**刻意差异**，换来的是一行自洽、演示时经得起"减一下"的记录。
      const planned = Number(activities.find((a) => a.id === item.activityId)?.hours) || 0
      item.status = 'SIGNED_OUT'
      item.signOutAt = plusHours(item.signInAt, planned) || now()
      item.hours = resolveMockHours(item)
      return ok(null, '签退成功，服务时长已记录')
    },
  },

  ]