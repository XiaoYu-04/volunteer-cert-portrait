import { users, roles, students, notices, logs } from './dataset'
import { DICT_DEFS } from '@/stores/dict'
import { ok, fail, paginate, like, eq, nextId, now } from './_helpers'

export default [
  /* ---------- 用户 ---------- */
  {
    method: 'get',
    path: '/v1/system/users',
    handler: ({ query }) => {
      const rows = users
        .filter((u) => like(u.username, query.keyword) || like(u.name, query.keyword))
        .filter((u) => eq(u.role, query.role))
        .filter((u) => eq(u.status, query.status))
        // 解构剔除密码。必须用重命名写法：直接写 ({ _password, ...rest })
        // 剔除的是 _password 这个键，password 会留在 rest 里被返回出去。
        .map(({ password: _password, ...rest }) => rest)
      return ok(paginate(rows, query))
    },
  },
  {
    method: 'post',
    path: '/v1/system/users',
    handler: ({ body }) => {
      if (users.some((u) => u.username === body.username)) {
        return fail(10001, '用户名已存在')
      }
      const id = nextId(users)
      users.push({
        id,
        username: body.username,
        password: body.password || '123456',
        name: body.name,
        role: body.role || 'STUDENT',
        roleLabel: { STUDENT: '学生', ORG_ADMIN: '组织管理员', SCHOOL_ADMIN: '学校管理员' }[body.role] || '学生',
        status: 'ACTIVE',
        studentId: null,
        orgId: body.orgId || null,
        phone: body.phone || '',
        email: body.email || '',
        createdAt: now(),
        lastLoginAt: '',
      })
      return ok(null, '新增成功')
    },
  },
  {
    method: 'put',
    path: '/v1/system/users/:id',
    handler: ({ params, body }) => {
      const user = users.find((u) => u.id === Number(params.id))
      if (!user) return fail(10002, '用户不存在')
      Object.assign(user, {
        name: body.name ?? user.name,
        role: body.role ?? user.role,
        phone: body.phone ?? user.phone,
        email: body.email ?? user.email,
      })
      return ok(null, '保存成功')
    },
  },
  {
    method: 'put',
    path: '/v1/system/users/:id/status',
    handler: ({ params, body }) => {
      const user = users.find((u) => u.id === Number(params.id))
      if (!user) return fail(10002, '用户不存在')
      user.status = body.status
      return ok(null, body.status === 'ACTIVE' ? '已启用' : '已停用')
    },
  },

  /* 管理员重置口令。口令留空即重置为默认口令 123456，与后端 DTO 的可空语义一致。

     真实后端有「连续 5 次密码错误锁定 15 分钟」，mock 故意不实现 —— 演示时被锁住
     只能去清 localStorage 才能继续，收益为负；mock 也不是安全边界。
     相应地，重置口令也不会顺带清空失败计数：mock 里没有这份状态。 */
  {
    method: 'put',
    path: '/v1/system/users/:id/password',
    handler: ({ params, body }) => {
      const user = users.find((u) => u.id === Number(params.id))
      if (!user) return fail(10002, '用户不存在')
      const password = String(body.password || '')
      if (password && password.length < 6) return fail(10001, '密码至少 6 位')
      if (password.length > 32) return fail(10001, '密码最多 32 位')
      // mock 不做哈希，直接存明文；真实后端存的是 BCrypt 密文，管理员也回显不出原口令。
      // 明文是为了演示闭环：重置成默认口令后，能当场登进去验一遍。
      user.password = password || '123456'
      return ok(null)
    },
  },
  {
    method: 'delete',
    path: '/v1/system/users/:id',
    handler: ({ params }) => {
      const idx = users.findIndex((u) => u.id === Number(params.id))
      if (idx === -1) return fail(10002, '用户不存在')
      users.splice(idx, 1)
      return ok(null, '已删除')
    },
  },

  /* ---------- 角色 ---------- */
  {
    method: 'get',
    path: '/v1/system/roles',
    handler: () => ok(roles),
  },

  /* ---------- 字典 ---------- */
  {
    method: 'get',
    path: '/v1/system/dicts',
    handler: () => ok(DICT_DEFS),
  },

  /* ---------- 学生档案 ---------- */
  {
    method: 'get',
    path: '/v1/system/students',
    handler: ({ query }) => {
      const rows = students
        .filter((s) => like(s.name, query.keyword) || like(s.studentNo, query.keyword))
        .filter((s) => eq(s.college, query.college))
        .filter((s) => eq(s.grade, query.grade))
      return ok(paginate(rows, query))
    },
  },
  {
    method: 'get',
    path: '/v1/system/students/:id',
    handler: ({ params }) => {
      const student = students.find((s) => s.id === Number(params.id))
      return student ? ok(student) : fail(10003, '学生档案不存在')
    },
  },

  /* ---------- 通知公告 ---------- */
  {
    method: 'get',
    path: '/v1/system/notifications',
    handler: ({ query }) => {
      let rows = notices.slice()
      if (query.unreadOnly === 'true' || query.unreadOnly === true) {
        rows = rows.filter((n) => !n.read)
      }
      rows = rows.filter((n) => like(n.title, query.keyword)).filter((n) => eq(n.type, query.type))
      return ok(paginate(rows, query))
    },
  },
  {
    method: 'get',
    path: '/v1/system/notifications/:id',
    handler: ({ params }) => {
      const item = notices.find((n) => n.id === Number(params.id))
      if (!item) return fail(10004, '通知不存在')
      item.read = true
      return ok(item)
    },
  },
  {
    method: 'put',
    path: '/v1/system/notifications/:id/read',
    handler: ({ params }) => {
      const item = notices.find((n) => n.id === Number(params.id))
      if (!item) return fail(10004, '通知不存在')
      item.read = true
      return ok(null, '已标记为已读')
    },
  },
  {
    method: 'post',
    path: '/v1/system/notifications',
    handler: ({ body }) => {
      notices.unshift({
        id: nextId(notices),
        title: body.title,
        // 正文：与后端 NotificationCreateDTO.content 对应，学生端详情页会渲染它
        content: body.content || '',
        date: now().slice(0, 10),
        from: body.from || '系统管理员',
        top: !!body.top,
        type: body.type || 'SYSTEM',
        read: false,
      })
      return ok(null, '发布成功')
    },
  },
  {
    method: 'delete',
    path: '/v1/system/notifications/:id',
    handler: ({ params }) => {
      const idx = notices.findIndex((n) => n.id === Number(params.id))
      if (idx === -1) return fail(10004, '通知不存在')
      notices.splice(idx, 1)
      return ok(null, '已删除')
    },
  },

  /* ---------- 操作日志 ---------- */
  {
    method: 'get',
    path: '/v1/system/logs',
    handler: ({ query }) => {
      const rows = logs
        .filter((l) => like(l.operator, query.keyword) || like(l.target, query.keyword))
        .filter((l) => eq(l.module, query.module))
      return ok(paginate(rows, query))
    },
  },
]
