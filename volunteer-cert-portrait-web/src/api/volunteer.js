import { get, post, put, del } from '@/utils/request'

/* ---------- 活动 ---------- */
export const listActivities = (params) => get('/v1/activities', params)
export const getActivity = (id) => get(`/v1/activities/${id}`)
export const createActivity = (data) => post('/v1/activities', data)
export const updateActivity = (id, data) => put(`/v1/activities/${id}`, data)
export const updateActivityStatus = (id, status) => put(`/v1/activities/${id}/status`, { status })
/** 删除草稿活动。后端只接受草稿，其它状态会返回 10001 */
export const deleteActivity = (id) => del(`/v1/activities/${id}`)

/** 组织端概览（注意：静态段路由，后端需排在 /activities/{id} 之前） */
export const getOrgOverview = (orgId) => get('/v1/activities/org-overview', { orgId })

/* ---------- 活动分类 ---------- */
export const listCategories = () => get('/v1/categories')
export const createCategory = (data) => post('/v1/categories', data)
export const updateCategory = (id, data) => put(`/v1/categories/${id}`, data)
export const deleteCategory = (id) => del(`/v1/categories/${id}`)

/* ---------- 报名 ---------- */
export const listSignups = (params) => get('/v1/signups', params)
export const createSignup = (data) => post('/v1/signups', data)
export const auditSignup = (id, data) => put(`/v1/signups/${id}/audit`, data)
export const cancelSignup = (id) => put(`/v1/signups/${id}/cancel`)

/* ---------- 签到 ---------- */
export const listAttendance = (params) => get('/v1/attendance', params)
export const updateAttendance = (id, data) => put(`/v1/attendance/${id}`, data)

/**
 * 学生本人的签到记录（待办 B24）。
 *
 * 数据范围由后端按登录态裁剪，前端传 studentId 无效；canSignIn / canSignOut 是
 * 服务端按 A2 的 30 分钟窗口算好的，前端不要重复窗口常量。
 * 注意是静态段路由：后端要把它排在 /attendance/{id} 之前。
 */
export const listMyAttendance = (params) => get('/v1/attendance/mine', params)

export const signIn = (attendanceId) => post('/v1/attendance/sign-in', { attendanceId })
export const signOut = (attendanceId) => post('/v1/attendance/sign-out', { attendanceId })