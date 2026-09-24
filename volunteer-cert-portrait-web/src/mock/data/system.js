import { users, roles, students, notices, logs, collegeDict, orgList } from './dataset'
import { collegeOptions } from './auth'
import { DICT_DEFS } from '@/stores/dict'
import { ok, fail, paginate, like, eq, nextId, now } from './_helpers'

/* ---------- 学院（sys_dict 里 dict_type = 'college' 的字典行）----------

   学院没有独立表，真库里就是 sys_dict 的一类字典项，因此这里的增删改
   直接作用在 dataset.js 的 collegeDict 上 —— 注册页那份下拉读的是同一份数据，
   删掉一个学院之后注册页立刻选不到它，与真库行为一致。

   学生数 / 组织数由 mock 的 students 与 orgList 现算（真库是 GROUP BY 聚合），
   学院管理页的「占用校验」用的就是这两个数。 */
function collegeUsage(name) {
  return {
    studentCount: students.filter((s) => s.college === name).length,
    orgCount: orgList.filter((o) => o.college === name).length,
  }
}

/** 学院列表行，形状与后端 CollegeVO 一致 */
function collegeRows() {
  return collegeDict
    .slice()
    .sort((a, b) => a.sort - b.sort || a.id - b.id)
    .map((c) => ({
      id: c.id,
      name: c.name,
      sort: c.sort,
      status: c.status ?? 1,
      ...collegeUsage(c.name),
    }))
}

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
      // 学院校验：与后端 UserServiceImpl.createUser 同一条规则、同一句文案。
      // 只认启用项 —— 停用过的学院在表单下拉里已经选不到，接口也得跟着拒，
      // 否则会出现「下拉选不到、接口却收得下」。
      const role = body.role || 'STUDENT'
      let college = null
      if (role === 'STUDENT') {
        college = String(body.college || '').trim()
        if (!college || !collegeDict.some((c) => c.name === college && (c.status ?? 1) === 1)) {
          return fail(10001, '请选择学院')
        }
      }

      const id = nextId(users)
      const user = {
        id,
        username: body.username,
        password: body.password || '123456',
        name: body.name,
        role,
        roleLabel: { STUDENT: '学生', ORG_ADMIN: '组织管理员', SCHOOL_ADMIN: '学校管理员' }[role] || '学生',
        status: 'ACTIVE',
        studentId: null,
        orgId: body.orgId || null,
        phone: body.phone || '',
        email: body.email || '',
        createdAt: now(),
        lastLoginAt: '',
      }
      users.push(user)

      // 后端建的是 student_info 那一行（学号用 S + 六位零填充的 userId 占位）。
      // mock 里同样补一行：学院管理页的学生数是现算的，不补的话
      // 「刚建的学生不算在学院占用里」，删学院时就会放行一个本该被拦的删除。
      if (role === 'STUDENT') {
        const studentId = nextId(students)
        user.studentId = studentId
        students.push({
          id: studentId,
          name: body.name,
          studentNo: `S${String(id).padStart(6, '0')}`,
          // 表单未采集的档案字段留空，与 /v1/auth/register 建档的写法一致
          gender: '',
          college,
          major: '',
          grade: '',
          className: '',
          phone: body.phone || '',
          totalHours: 0,
          profileTag: '',
        })
      }
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
      // 存明文是为了演示方便：重置成默认口令后，能当场登进去验一遍。
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
    // 真库的 sys_dict 里除了 7 类状态字典，还有一类 college（学院，dict_key 与
    // dict_value 同值）。后端 listDicts() 返回全部启用项，因此这里也必须带上它 ——
    // 前端字典 store 是「整体替换」语义，少了这一类，管理端的学院筛选下拉就会空着。
    handler: () => ok({ ...DICT_DEFS, college: collegeOptions() }),
  },

  /* ---------- 学院 ---------- */
  {
    method: 'get',
    path: '/v1/system/colleges',
    // 刻意不过滤 status：管理页要能看到停用项并重新启用它们，
    // 与后端 CollegeController 同口径（只取启用项的是注册页那份 /v1/auth/colleges）。
    handler: () => ok(collegeRows()),
  },
  {
    method: 'post',
    path: '/v1/system/colleges',
    handler: ({ body }) => {
      const name = String(body.name || '').trim()
      if (!name) return fail(10001, '请填写学院名称')
      if (name.length > 30) return fail(10001, '学院名称最多 30 个字')
      if (collegeDict.some((c) => c.name === name)) return fail(10001, '该学院已存在')

      const sort =
        Number(body.sort) > 0
          ? Number(body.sort)
          : collegeDict.reduce((max, c) => Math.max(max, c.sort), 0) + 1
      collegeDict.push({ id: nextId(collegeDict), name, sort, status: 1 })
      return ok(null, '学院已新增')
    },
  },
  {
    method: 'put',
    path: '/v1/system/colleges/:id/status',
    // 停用不校验占用：学院合并或停招时，硬删会被占用校验挡住，只能靠停用
    // 让它从注册页下拉里消失，已有数据不受影响。
    handler: ({ params, body }) => {
      const item = collegeDict.find((c) => c.id === Number(params.id))
      if (!item) return fail(10001, '学院不存在')

      // 与后端 CollegeServiceImpl.parseStatus 同口径：ACTIVE / DISABLED 是前端口径，
      // "1" / "0" 也一并接受（StatusUpdateDTO.status 是字符串，数字会被序列化成字符串），
      // 其余一律拒绝 —— 静默按「启用」处理会让停用按钮看着生效、实际没改。
      const raw = String(body.status ?? '').trim()
      if (raw === 'ACTIVE' || raw === '1') item.status = 1
      else if (raw === 'DISABLED' || raw === '0') item.status = 0
      else return fail(10001, '学院状态不合法')

      return ok(null, item.status === 1 ? '学院已启用' : '学院已停用')
    },
  },
  {
    method: 'delete',
    path: '/v1/system/colleges/:id',
    handler: ({ params }) => {
      const idx = collegeDict.findIndex((c) => c.id === Number(params.id))
      if (idx === -1) return fail(10001, '学院不存在')

      // 占用校验：学院是「按学院统计」的分组键，删掉还有数据的学院会把那批记录
      // 变成查不到的分组，因此先挡住、并说清挡在哪儿（文案与后端逐字一致）。
      const { name } = collegeDict[idx]
      const { studentCount, orgCount } = collegeUsage(name)
      if (studentCount && orgCount) {
        return fail(10001, `该学院下还有 ${studentCount} 名学生、${orgCount} 个组织，不能删除`)
      }
      if (studentCount) return fail(10001, `该学院下还有 ${studentCount} 名学生，不能删除`)
      if (orgCount) return fail(10001, `该学院下还有 ${orgCount} 个组织，不能删除`)

      collegeDict.splice(idx, 1)
      return ok(null, '学院已删除')
    },
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
