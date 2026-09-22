import { get, put } from '@/utils/request'

export const listOrgs = (params) => get('/v1/orgs', params)
export const getOrg = (id) => get(`/v1/orgs/${id}`)

/** 组织资质审核，action: 'APPROVE' | 'REJECT' */
export const auditOrg = (id, data) => put(`/v1/orgs/${id}/audit`, data)
export const updateOrgStatus = (id, status) => put(`/v1/orgs/${id}/status`, { status })