import { get, post, put, del } from '@/utils/request'

/* ---------- 用户与权限 ---------- */
export const listUsers = (params) => get('/v1/system/users', params)
export const createUser = (data) => post('/v1/system/users', data)
export const updateUser = (id, data) => put(`/v1/system/users/${id}`, data)
export const updateUserStatus = (id, status) => put(`/v1/system/users/${id}/status`, { status })
export const deleteUser = (id) => del(`/v1/system/users/${id}`)

/** 管理员重置指定用户的口令 */
export const resetUserPassword = (id, data) => put(`/v1/system/users/${id}/password`, data)

export const listRoles = () => get('/v1/system/roles')

/* ---------- 字典 ---------- */
export const listDicts = () => get('/v1/system/dicts')

/* ---------- 学生档案 ---------- */
export const listStudents = (params) => get('/v1/system/students', params)
export const getStudent = (id) => get(`/v1/system/students/${id}`)

/* ---------- 通知公告 ---------- */
export const listNotifications = (params) => get('/v1/system/notifications', params)
export const getNotification = (id) => get(`/v1/system/notifications/${id}`)
export const markNotificationRead = (id) => put(`/v1/system/notifications/${id}/read`)
export const createNotification = (data) => post('/v1/system/notifications', data)
export const deleteNotification = (id) => del(`/v1/system/notifications/${id}`)

/* ---------- 操作日志 ---------- */
export const listLogs = (params) => get('/v1/system/logs', params)
