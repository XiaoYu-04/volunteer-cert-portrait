import { get, post, put } from '@/utils/request'

/** 登录，返回 { token, user } */
export const login = (data) => post('/v1/auth/login', data)

/** 注册（仅学生自助注册） */
export const register = (data) => post('/v1/auth/register', data)

export const logout = () => post('/v1/auth/logout')

/** 当前登录用户信息 */
export const getCurrentUser = () => get('/v1/auth/me')

export const updateProfile = (data) => put('/v1/auth/me', data)

/** 修改本人密码 */
export const changePassword = (data) => put('/v1/auth/password', data)
