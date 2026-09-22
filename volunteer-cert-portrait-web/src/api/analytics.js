import { get } from '@/utils/request'

/** 看板全量数据，首页/数据看板一次拉齐 */
export const getDashboard = () => get('/v1/analytics/dashboard')

export const getOverview = () => get('/v1/analytics/overview')
export const getTrend = () => get('/v1/analytics/trend')
export const getTypes = () => get('/v1/analytics/types')
export const getColleges = () => get('/v1/analytics/colleges')
export const getOrgs = () => get('/v1/analytics/orgs')
export const getProfiles = () => get('/v1/analytics/profiles')
export const getAudit = () => get('/v1/analytics/audit')
export const getSignin = () => get('/v1/analytics/signin')
export const getHeatmap = () => get('/v1/analytics/heatmap')