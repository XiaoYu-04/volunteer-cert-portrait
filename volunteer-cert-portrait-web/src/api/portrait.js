import { get } from '@/utils/request'

/** 当前学生的公益画像 */
export const getMyPortrait = () => get('/v1/portraits/me')

/** 画像标签分布（六类） */
export const getDistribution = () => get('/v1/portraits/distribution')

export const listPortraits = (params) => get('/v1/portraits', params)
export const getPortrait = (studentId) => get(`/v1/portraits/${studentId}`)