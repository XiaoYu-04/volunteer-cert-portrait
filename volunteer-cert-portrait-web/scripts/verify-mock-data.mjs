/**
 * 模拟数据集的自洽校验。
 *
 *   npm run verify:mock
 *
 * src/mock/data/dataset.js 里的聚合指标继承自设计原型，改动任一数值都可能
 * 让不同页面显示的数字互相打架。这个脚本把那些隐性约束写成断言，改完数据跑一遍即可。
 */
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const datasetPath = resolve(here, '../src/mock/data/dataset.js')
const D = await import(`file://${datasetPath.replace(/\\/g, '/')}`)

const sum = (rows, pick) => rows.reduce((total, row) => total + pick(row), 0)

const passed = []
const failed = []
const check = (name, ok, detail) => {
  ;(ok ? passed : failed).push(`${ok ? '✓' : '✗'} ${name}${detail ? ` — ${detail}` : ''}`)
}

/* ---------- 原型的冻结等式 ---------- */
check('学院时长合计 = 86,420 = 累计志愿时长', sum(D.colleges, (c) => c.hours) === 86420)
check('活动类型合计 = 386 = 活动总数', sum(D.types, (t) => t.value) === 386)
check('公益画像合计 = 12,480 = 报名总人数', sum(D.profiles, (p) => p.count) === 12480)
check('月度活动合计 = 386', sum(D.trend, (t) => t.count) === 386)
check('月度时长合计 = 86,420', sum(D.trend, (t) => t.hours) === 86420)
check(
  '签到率 = 2,290 / 2,480 = 92.3%',
  D.signin.signed === 2290 &&
    D.signin.total === 2480 &&
    +(D.signin.signed / D.signin.total).toFixed(3) === 0.923,
)
check('审核三态合计 = 2,418', sum(D.audit.items, (i) => i.value) === 2418)
check(
  '审核通过率 = 2,142 / 2,418 = 88.6%',
  +(D.audit.items[0].value / D.audit.total).toFixed(3) === D.audit.passRate,
)

/* ---------- 实体数据一致性 ---------- */
const names = D.students.map((s) => s.name)
check('学生姓名唯一', new Set(names).size === names.length, `${names.length} 人`)
check('学号唯一', new Set(D.students.map((s) => s.studentNo)).size === D.students.length)

const signupPairs = D.signups.map((s) => `${s.studentId}-${s.activityId}`)
check(
  '无重复「学生 + 活动」报名',
  new Set(signupPairs).size === signupPairs.length,
  `${signupPairs.length} 条`,
)

const act1 = D.activities[0]
const act1Valid = D.signups.filter(
  (s) => s.activityId === act1.id && s.status !== 'REJECTED' && s.status !== 'CANCELED',
).length
check(
  '活动 1 有效报名数 = enrolled（活动卡片按 enrolled 渲染）',
  act1Valid === act1.enrolled,
  `列表 ${act1Valid} / enrolled ${act1.enrolled}`,
)

const durationPairs = D.durations.map((d) => `${d.studentId}-${d.activityId}`)
check(
  '无重复「学生 + 活动」时长',
  new Set(durationPairs).size === durationPairs.length,
  `${durationPairs.length} 条`,
)

/* ---------- 分类与标签（A12 / B20-4 按后端对齐后新增的约束）---------- */
const mismatched = D.activities.filter(
  (a) => (D.categories.find((c) => c.id === a.categoryId) || {}).name !== a.type,
)
check(
  '每场活动的分类 id 与类型名一致',
  mismatched.length === 0,
  mismatched.length ? `不一致：活动 ${mismatched.map((a) => a.id)}` : `${D.activities.length} 场`,
)

const typeNames = D.types.map((t) => t.name)
check(
  '分类名唯一，且活动用到的类型都在分类表里',
  new Set(typeNames).size === typeNames.length && D.activities.every((a) => typeNames.includes(a.type)),
)

const tagCounts = D.profiles.map((p) => p.count)
check(
  '画像标签为后端那 8 类，且按人数降序（页面取 distribution[0] 当「最大标签」）',
  D.profiles.length === 8 &&
    new Set(D.profiles.map((p) => p.tag)).size === 8 &&
    tagCounts.every((c, i) => i === 0 || tagCounts[i - 1] >= c),
)

/* ---------- 演示账号（登录名 student）----------
   这名学生是演示时第一个被点开的账号，它的「我的时长」汇总与
   「我的公益画像」累计时长必须一致，否则一眼就能看出对不上。 */
const demo = D.students[0]
const approvedHours = sum(
  D.durations.filter((d) => d.studentId === demo.id && d.status === 'APPROVED'),
  (d) => d.hours,
)
check('演示账号已通过时长 = 画像累计时长', approvedHours === demo.totalHours, `${approvedHours} 小时`)

const demoSignupActs = new Set(
  D.signups.filter((s) => s.studentId === demo.id).map((s) => s.activityId),
)
const orphan = D.durations
  .filter((d) => d.studentId === demo.id)
  .map((d) => d.activityId)
  .filter((id) => !demoSignupActs.has(id))
check('演示账号每条时长都有对应报名', orphan.length === 0, orphan.length ? `孤立活动 ${orphan}` : '')

/* ---------- 行内字段一致性（C13~C16 / 待办第 26 项）----------
   上面那些断言覆盖的是**聚合之间**（学院合计 = 累计时长）与**枚举覆盖**
   （四种签到状态都出现），但不覆盖**同一行内字段之间**的一致性 ——
   所以「签到 08:45 → 签退 12:05 却记 4 小时」这类矛盾一直没被拦住。
   这类问题的共同点是静态检查看不出来、真跑也不报错，只有断言能拦。 */

const parseAt = (text) => {
  const m = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})$/.exec(text || '')
  return m ? new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6]) : null
}

const inconsistentHours = D.attendance.filter((a) => {
  if (a.status !== 'SIGNED_OUT') return false
  const signIn = parseAt(a.signInAt)
  const signOut = parseAt(a.signOutAt)
  if (!signIn || !signOut) return true
  return Math.abs((signOut - signIn) / 3600000 - a.hours) > 1e-6
})
check(
  '已签退记录的实得时长 = (签退 − 签到) / 3600（后端口径，对应 C14）',
  inconsistentHours.length === 0,
  inconsistentHours.length
    ? `${inconsistentHours.length} 条不自洽，如 ${inconsistentHours[0].signInAt} → ${inconsistentHours[0].signOutAt} 记 ${inconsistentHours[0].hours} 小时`
    : `${D.attendance.filter((a) => a.status === 'SIGNED_OUT').length} 条`,
)

// 脱敏串一旦被回填进可编辑表单，就必然过不了 /^1\d{10}$/（C13 的成因）——
// 个人中心与用户管理都会预填 phone。库里存的是完整号码，mock 也不该存脱敏串。
const maskedPhones = [...D.users, ...D.students, ...D.orgList].filter((r) => /\*/.test(r.phone || ''))
check(
  'phone 存完整 11 位号码，不得是脱敏串（会被回填进可编辑表单，对应 C13）',
  maskedPhones.length === 0,
  maskedPhones.length ? `${maskedPhones.length} 处，如 ${maskedPhones[0].phone}` : '',
)

const noRemark = D.categories.filter((c) => !c.remark)
check(
  '分类说明非空（分类管理页「说明」列，对应 C15）',
  noRemark.length === 0,
  noRemark.length ? `${noRemark.length} 个分类缺说明` : `${D.categories.length} 个分类`,
)

/* ---------- 其他 ---------- */
const attendanceStatuses = new Set(D.attendance.map((a) => a.status))
check(
  '签到记录覆盖四种状态',
  ['SIGNED_OUT', 'SIGNED_IN', 'ABNORMAL', 'ABSENT'].every((s) => attendanceStatuses.has(s)),
)

check(
  '用户关联的 studentId 均可解析',
  D.users.filter((u) => u.studentId).every((u) => D.students.some((s) => s.id === u.studentId)),
)
check('演示账号 student 对应学生 1', D.users.find((u) => u.username === 'student').studentId === 1)

/* ---------- 输出 ---------- */
console.log(passed.join('\n'))
if (failed.length) {
  console.error(`\n${failed.join('\n')}\n\n✗ ${failed.length} 项未通过`)
  process.exit(1)
}
console.log(`\n✓ 全部 ${passed.length} 项通过`)