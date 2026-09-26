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

/* ---------- 学院 ---------- */
/**
 * 学院不是独立表，是 `sys_dict` 里 `dict_type = 'college'` 的字典行。
 * 这组接口给学校管理端的学院管理页用；注册页那份下拉走的是免登录的
 * `GET /v1/auth/colleges`（见 api/auth.js），两者读的是同一份数据。
 *
 * 列表**不过滤停用项** —— 管理页要能看到并重新启用它们；注册页那份只取启用项。
 */
export const listColleges = () => get('/v1/system/colleges')
export const createCollege = (data) => post('/v1/system/colleges', data)
export const deleteCollege = (id) => del(`/v1/system/colleges/${id}`)
export const updateCollegeStatus = (id, status) =>
  put(`/v1/system/colleges/${id}/status`, { status })

/* ---------- 通知公告 ---------- */
export const listNotifications = (params) => get('/v1/system/notifications', params)
export const getNotification = (id) => get(`/v1/system/notifications/${id}`)
export const markNotificationRead = (id) => put(`/v1/system/notifications/${id}/read`)
export const createNotification = (data) => post('/v1/system/notifications', data)
export const deleteNotification = (id) => del(`/v1/system/notifications/${id}`)

/* ---------- 操作日志 ---------- */
export const listLogs = (params) => get('/v1/system/logs', params)
