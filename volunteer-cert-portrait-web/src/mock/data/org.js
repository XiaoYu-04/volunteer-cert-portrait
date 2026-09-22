import { orgList } from './dataset'
import { ok, fail, paginate, like, eq } from './_helpers'

export default [
  {
    method: 'get',
    path: '/v1/orgs',
    handler: ({ query }) => {
      const rows = orgList
        .filter((o) => like(o.name, query.keyword))
        .filter((o) => eq(o.status, query.status))
        .filter((o) => eq(o.college, query.college))
      return ok(paginate(rows, query))
    },
  },
  {
    method: 'get',
    path: '/v1/orgs/:id',
    handler: ({ params }) => {
      const org = orgList.find((o) => o.id === Number(params.id))
      return org ? ok(org) : fail(10005, '组织不存在')
    },
  },
  {
    method: 'put',
    path: '/v1/orgs/:id/audit',
    handler: ({ params, body }) => {
      const org = orgList.find((o) => o.id === Number(params.id))
      if (!org) return fail(10005, '组织不存在')
      if (org.status !== 'PENDING') return fail(10006, '该组织已审核，无法重复操作')
      org.status = body.action === 'APPROVE' ? 'APPROVED' : 'REJECTED'
      org.auditRemark = body.remark || ''
      return ok(null, body.action === 'APPROVE' ? '资质已通过' : '资质已驳回')
    },
  },
  {
    method: 'put',
    path: '/v1/orgs/:id/status',
    handler: ({ params, body }) => {
      const org = orgList.find((o) => o.id === Number(params.id))
      if (!org) return fail(10005, '组织不存在')
      org.status = body.status
      return ok(null, '状态已更新')
    },
  },
]