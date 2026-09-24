import { get, post, put } from '@/utils/request'

/** 登录，返回 { token, user } */
export const login = (data) => post('/v1/auth/login', data)

/** 注册（仅学生自助注册） */
export const register = (data) => post('/v1/auth/register', data)

/** 注册页的学院选项。免登录 —— 注册页本身在登录之前 */
export const getColleges = () => get('/v1/auth/colleges')

export const logout = () => post('/v1/auth/logout')

/** 当前登录用户信息 */
export const getCurrentUser = () => get('/v1/auth/me')

export const updateProfile = (data) => put('/v1/auth/me', data)

/** 修改本人密码 */
export const changePassword = (data) => put('/v1/auth/password', data)
