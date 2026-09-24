import { users, students, collegeDict } from './dataset'
import { ok, fail, currentUserId, nextId, now, persistUser } from './_helpers'

/* 注册校验。前端已拦一道，这里再拦一道 —— 客户端校验只是体验优化，
   不能当成数据守门人，真实后端同样必须自行校验。 */
const RE_USERNAME = /^[a-zA-Z0-9_]{4,20}$/
const RE_PHONE = /^1[3-9]\d{9}$/
const RE_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/* 学院下拉选项：形状与字典接口的 {value,label,tone} 一致 —— 注册页只取 value/label，
   tone 是给状态标签用的色调，学院没有状态语义，统一中性色。

   数据源是 dataset.js 的 collegeDict（可增删可停用），**不是**一份写死的副本：
   学校管理端的学院管理页改的就是那一份，改完注册页下拉立刻跟着变，
   这与真库「两处读同一个 sys_dict」的行为一致。
   停用项（status = 0）在这里就被滤掉，与后端 DictService.listByType 只取启用项同口径。 */
export function collegeOptions() {
  return collegeDict
    .filter((c) => c.status !== 0)
    .slice()
    .sort((a, b) => a.sort - b.sort || a.id - b.id)
    .map((c) => ({ value: c.name, label: c.name, tone: 'mute' }))
}

/** 把用户记录转成前端会话对象（不含密码） */
function toSession(user) {
  const student = user.studentId ? students.find((s) => s.id === user.studentId) : null
  return {
    id: user.id,
    username: user.username,
    name: user.name,
    role: user.role,
    roleLabel: user.roleLabel,
    orgId: user.orgId || null,
    studentId: user.studentId || null,
    college: student ? student.college : null,
    studentNo: student ? student.studentNo : null,
    // 联系方式：与后端 SessionVO 一致，个人资料页要用它回显
    phone: user.phone || '',
    email: user.email || '',
    avatarText: user.name.slice(-2),
  }
}

export default [
  /* 注册页的学院下拉数据源。免登录 —— 注册页本身在登录之前，这个接口不能要 token */
  {
    method: 'get',
    path: '/v1/auth/colleges',
    handler: () => ok(collegeOptions()),
  },

  {
    method: 'post',
    path: '/v1/auth/login',
    handler: ({ body }) => {
      const user = users.find((u) => u.username === body.username)
      if (!user || user.password !== body.password) {
        return fail(20001, '用户名或密码错误')
      }
      if (user.status === 'DISABLED') {
        return fail(20002, '账号已停用，请联系学校管理员')
      }
      user.lastLoginAt = now()
      return ok({
        token: `mock-token-${user.id}`,
        user: toSession(user),
      })
    },
  },

  {
    method: 'post',
    path: '/v1/auth/register',
    handler: ({ body }) => {
      if (!RE_USERNAME.test(body.username || '')) {
        return fail(10001, '用户名为 4-20 位字母、数字或下划线')
      }
      if (!String(body.name || '').trim()) {
        return fail(10001, '姓名不能为空')
      }
      // 学院必须命中字典里的启用项，不接受自由文本 —— 同一学院一旦有第二种写法，
      // 「按学院统计」就会把它算成另一个学院（与后端 AuthServiceImpl.register 同口径）。
      // 这条也保证「管理端删掉一个学院后，注册页再提交它一定被拒」。
      const college = String(body.college || '').trim()
      if (!college || !collegeDict.some((c) => c.status !== 0 && c.name === college)) {
        return fail(10001, '请选择学院')
      }
      if (!body.password || String(body.password).length < 6) {
        return fail(10001, '密码至少 6 位')
      }
      if (!RE_PHONE.test(body.phone || '')) {
        return fail(10001, '请输入 11 位手机号')
      }
      if (!RE_EMAIL.test(body.email || '')) {
        return fail(10001, '请输入有效的邮箱地址')
      }
      if (users.some((u) => u.username === body.username)) {
        return fail(10001, '用户名已存在')
      }
      if (users.some((u) => u.phone === body.phone)) {
        return fail(10001, '该手机号已被注册')
      }
      const id = nextId(users)

      /* 建档：会话里的 college / studentNo 都从 students 里取，漏建就会出现
         「注册成功、个人资料页却没有学院」。顺序也照后端 register 来 —— 先建档再建会话。
         学号写占位值「S + 六位零填充的用户 id」：注册表单不收学号，而档案里这一项必填，
         由主键派生既保证唯一，也和后端 StudentArchiveRegistrar 的口径一致。
         注意 students 不落盘（_helpers 只持久化账号），刷新后这份档案随 mock 重置，
         与「注册后刷新仍能登录」的既有取舍保持一致。 */
      const student = {
        id: nextId(students),
        name: body.name,
        studentNo: `S${String(id).padStart(6, '0')}`,
        // 注册表单未采集的档案字段留空，等管理员在用户管理里补全
        gender: '',
        college: body.college,
        major: '',
        grade: '',
        className: '',
        phone: body.phone || '',
        // 新账号还没有审核通过的时长，累计时长与画像标签都从零起步
        totalHours: 0,
        profileTag: '',
      }
      students.push(student)

      const user = {
        id,
        username: body.username,
        password: body.password,
        name: body.name,
        role: 'STUDENT',
        roleLabel: '学生',
        status: 'ACTIVE',
        studentId: student.id,
        phone: body.phone || '',
        email: body.email || '',
        createdAt: now(),
        lastLoginAt: '',
      }
      users.push(user)
      // 落盘，否则整页刷新后 mock 重置，新账号连同登录态一起消失
      persistUser(user)
      return ok({ token: `mock-token-${id}`, user: toSession(user) }, '注册成功')
    },
  },

  {
    method: 'post',
    path: '/v1/auth/logout',
    handler: () => ok(null, '已退出登录'),
  },

  {
    method: 'get',
    path: '/v1/auth/me',
    handler: ({ headers }) => {
      const id = currentUserId(headers)
      const user = users.find((u) => u.id === id)
      if (!user) return fail(20001, '登录已过期，请重新登录')
      return ok(toSession(user))
    },
  },

  {
    method: 'put',
    path: '/v1/auth/me',
    handler: ({ headers, body }) => {
      const id = currentUserId(headers)
      const user = users.find((u) => u.id === id)
      if (!user) return fail(20001, '登录已过期，请重新登录')
      if (body.name) user.name = body.name
      if (body.phone) user.phone = body.phone
      if (body.email) user.email = body.email
      return ok(toSession(user), '保存成功')
    },
  },

  /* 修改当前登录用户的密码。
     校验顺序照契约来：先原密码、再新密码格式、最后新旧是否相同 —— 原密码填错时
     先报「原密码不正确」，用户才知道该改哪一栏，不会被格式提示带偏。

     取舍：真实后端有「连续 5 次原密码错误锁定 15 分钟」，mock 故意不实现 ——
     演示时被锁住只能去清 localStorage 才能继续，收益为负；mock 也不是安全边界，
     没有防爆破的需求。所以这里只判单次原密码，不做失败计数与锁定。 */
  {
    method: 'put',
    path: '/v1/auth/password',
    handler: ({ headers, body }) => {
      const id = currentUserId(headers)
      const user = users.find((u) => u.id === id)
      if (!user) return fail(20001, '登录已过期，请重新登录')
      if (user.password !== body.oldPassword) {
        return fail(20005, '原密码不正确')
      }
      const newPassword = String(body.newPassword || '')
      if (newPassword.length < 6) return fail(10001, '密码至少 6 位')
      if (newPassword.length > 32) return fail(10001, '密码最多 32 位')
      // 走到这里 user.password 就是原密码，直接拿它比即可
      if (newPassword === user.password) {
        return fail(10001, '新密码不能与原密码相同')
      }
      // mock 不做哈希，直接存明文；真实后端存的是 BCrypt 密文，比对也发生在服务端。
      // 存明文是为了演示方便：改成什么，就能当场用什么登进去验一遍。
      user.password = newPassword
      return ok(null)
    },
  },
]
