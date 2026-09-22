import { get, post, put, del } from '@/utils/request'

/* ---------- 活动 ---------- */
export const listActivities = (params) => get('/v1/activities', params)
export const getActivity = (id) => get(`/v1/activities/${id}`)
export const createActivity = (data) => post('/v1/activities', data)
export const updateActivity = (id, data) => put(`/v1/activities/${id}`, data)
export const updateActivityStatus = (id, status) => put(`/v1/activities/${id}/status`, { status })

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
export const signIn = (attendanceId) => post('/v1/attendance/sign-in', { attendanceId })
export const signOut = (attendanceId) => post('/v1/attendance/sign-out', { attendanceId })