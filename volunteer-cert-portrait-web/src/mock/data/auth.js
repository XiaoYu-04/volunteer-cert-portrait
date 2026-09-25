import { users, students, collegeDict } from './dataset'
import { ok, fail, currentUserId, nextId, now, persistUser } from './_helpers'

/* 注册校验。前端已拦一道，这里再拦一道 —— 客户端校验只是体验优化，
   不能当成数据守门人，真实后端同样必须自行校验。 */
const RE_USERNAME = /^[a-zA-Z0-9_]{4,20}$/
/* 学号：4-20 位纯数字。注册的格式校验与登录的「纯数字 ⇒ 当学号查」共用这一条，
   system.js 新增学生那条 handler 也 import 它 —— 两处各写一份的话，改了位数区间
   就会出现「注册能收下、登录却认不出」。 */
export const RE_STUDENT_NO = /^[0-9]{4,20}$/
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

  /* 登录：请求体字段仍叫 username，语义是「用户名**或**学号」（契约冻结，别改名）。
     识别规则与后端 AuthServiceImpl 完全一致：trim 后是纯数字就当学号查，
     否则当用户名查 —— 判据只用「形态」，不看这个字符串是否真的存在的账号，
     所以「纯数字但不存在」与「字母开头但不存在」两条路径返回的是同一句话。

     纯数字走学号路径时还带一次兜底：按学号查不到，再按用户名查一次。
     学号与用户名是两个命名空间（前者在 students.studentNo，后者在 users.username），
     完全可能撞在一起 —— 少了这次兜底，「用户名叫 20230001」的人就只能改用户名。 */
  {
    method: 'post',
    path: '/v1/auth/login',
    handler: ({ body }) => {
      const account = String(body.username == null ? '' : body.username).trim()
      // 学号路径：studentNo → student.id → user.studentId，两次索引缺一不可。
      // 只按 studentNo 找 student 是不够的：账号那一行认的是 students.id。
      const matchedStudent = RE_STUDENT_NO.test(account)
        ? students.find((s) => s.studentNo === account)
        : null
      const user =
        (matchedStudent && users.find((u) => u.studentId === matchedStudent.id)) ||
        users.find((u) => u.username === account)
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
      /* 学号：格式必须合法，必填。位置紧跟姓名 —— 与后端 AuthServiceImpl.register
         逐行对齐（那边也是 username → name → studentNo → password → …），
         同时与前端注册表单的字段顺序（用户名 / 姓名 / 学号 / 学院）一致，
         出错时高亮的正是用户眼里从上往下第一个没填对的框。
         空着不填与格式写错给同一条提示：对用户来说是同一件事。
         查重不在这里，排在下面那组查重里 —— 顺序反了的话，一个格式非法的输入
         会先报「已被注册」，把人引到错误的修改方向。 */
      const studentNo = String(body.studentNo == null ? '' : body.studentNo).trim()
      if (!RE_STUDENT_NO.test(studentNo)) {
        return fail(10001, '学号为 4-20 位数字')
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
      /* 学号查重与后端 register 同序，排在那两条查重之后（用户名 → 手机号 → 学号）。
         文案「该学号已被注册」与后端一字不差。
         学号是登录凭据之一（登录支持用户名或学号），撞号等于两个人抢同一个登录名。
         库里 student_no 上有 UNIQUE 约束兜底，mock 里就靠这条内存查重。
         注意它只查 students：mock 没有「逻辑删除」概念，档案删掉就是真删，
         因此不存在后端那种「已被逻辑删除的学号查不出、却仍会撞库级唯一约束」的残留路径。 */
      if (students.some((s) => s.studentNo === studentNo)) {
        return fail(10001, '该学号已被注册')
      }
      const id = nextId(users)

      /* 建档：会话里的 college / studentNo 都从 students 里取，漏建就会出现
         「注册成功、个人资料页却没有学院」。顺序也照后端 register 来 —— 先建档再建会话。
         学号写用户填的那个值。这里原来写的是「S + 六位零填充的用户 id」占位值，
         理由是「注册表单不收学号」—— 表单现在收了，那条理由不再成立；
         占位值还带来一个真实缺陷：学号形态是纯数字，S 开头的占位串永远登不进去，
         「学号也能登录」对自助注册的账号就是空话。
         唯一性由上面那条查重保证（与 student_no 的唯一约束同口径）。
         注意 students 不落盘（_helpers 只持久化账号），刷新后这份档案随 mock 重置，
         与「注册后刷新仍能登录」的既有取舍保持一致 —— 也就是说刷新后 user.studentId
         指向的档案行没了，个人资料页的学院与学号会空，账号本身仍能登。 */
      const student = {
        id: nextId(students),
        name: body.name,
        studentNo,
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
