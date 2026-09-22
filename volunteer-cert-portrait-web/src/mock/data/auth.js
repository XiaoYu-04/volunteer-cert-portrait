import { users, students } from './dataset'
import { ok, fail, currentUserId, nextId, now } from './_helpers'

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
    avatarText: user.name.slice(-2),
  }
}

export default [
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
      if (users.some((u) => u.username === body.username)) {
        return fail(10001, '用户名已存在')
      }
      const id = nextId(users)
      const user = {
        id,
        username: body.username,
        password: body.password,
        name: body.name,
        role: 'STUDENT',
        roleLabel: '学生',
        status: 'ACTIVE',
        studentId: null,
        phone: body.phone || '',
        email: body.email || '',
        createdAt: now(),
        lastLoginAt: '',
      }
      users.push(user)
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
]