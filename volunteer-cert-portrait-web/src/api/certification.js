import { get, post, put } from '@/utils/request'

/* ---------- 服务时长 ---------- */
export const listDurations = (params) => get('/v1/durations', params)
export const getDuration = (id) => get(`/v1/durations/${id}`)

/** 提交时长，body 支持单条或 { items: [...] } 批量 */
export const submitDurations = (data) => post('/v1/durations', data)

/** 单条审核，action: 'APPROVE' | 'REJECT' */
export const auditDuration = (id, data) => put(`/v1/durations/${id}/audit`, data)

/** 批量审核，body: { ids, action, remark } */
export const batchAuditDurations = (data) => post('/v1/durations/batch-audit', data)

export const getAuditSummary = () => get('/v1/duration-audits/summary')