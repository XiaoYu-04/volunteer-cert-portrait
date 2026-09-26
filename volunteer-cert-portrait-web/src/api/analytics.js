import { get } from '@/utils/request'

/** 看板全量数据，首页/数据看板一次拉齐 */
export const getDashboard = () => get('/v1/analytics/dashboard')