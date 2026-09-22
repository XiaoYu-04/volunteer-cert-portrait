import { durations, audit, students } from './dataset'
import { ok, fail, paginate, like, eq, nextId, now } from './_helpers'

export default [
  /* ---------- 服务时长 ---------- */
  {
    method: 'get',
    path: '/v1/durations',
    handler: ({ query }) => {
      const rows = durations
        .filter((d) => like(d.studentName, query.keyword) || like(d.activityTitle, query.keyword))
        .filter((d) => eq(d.status, query.status))
        .filter((d) => eq(d.college, query.college))
        .filter((d) => eq(d.orgId, query.orgId))
        .filter((d) => eq(d.studentId, query.studentId))
      return ok(paginate(rows, query))
    },
  },
  {
    method: 'get',
    path: '/v1/durations/:id',
    handler: ({ params }) => {
      const item = durations.find((d) => d.id === Number(params.id))
      return item ? ok(item) : fail(40001, '时长记录不存在')
    },
  },
  {
    method: 'post',
    path: '/v1/durations',
    handler: ({ body }) => {
      const rows = Array.isArray(body.items) ? body.items : [body]
      const created = rows.map((row) => {
        const student = students.find((s) => s.id === Number(row.studentId)) || students[0]
        const id = nextId(durations)
        durations.unshift({
          id,
          studentId: student.id,
          studentName: student.name,
          studentNo: student.studentNo,
          college: student.college,
          activityId: Number(row.activityId) || 1,
          activityTitle: row.activityTitle || '社区敬老院陪伴服务',
          activityType: row.activityType || '社区服务',
          orgId: Number(row.orgId) || 1,
          orgName: row.orgName || '青年志愿者协会',
          hours: Number(row.hours) || 0,
          serviceDate: row.serviceDate || now().slice(0, 10),
          status: 'PENDING_AUDIT',
          submittedAt: now(),
          auditedAt: '',
          auditor: '',
          remark: '',
          proof: row.proof || '',
        })
        return id
      })
      return ok({ ids: created }, `已提交 ${created.length} 条服务时长，等待学校审核`)
    },
  },
  {
    method: 'put',
    path: '/v1/durations/:id/audit',
    handler: ({ params, body }) => {
      const item = durations.find((d) => d.id === Number(params.id))
      if (!item) return fail(40001, '时长记录不存在')
      if (item.status !== 'PENDING_AUDIT') return fail(40002, '该记录已审核，无法重复操作')

      item.status = body.action === 'APPROVE' ? 'APPROVED' : 'REJECTED'
      item.auditor = body.auditor || '赵慧敏'
      item.auditedAt = now()
      item.remark = body.action === 'APPROVE' ? '' : body.remark || '服务时长与签到记录不符'
      return ok(null, body.action === 'APPROVE' ? '已通过' : '已驳回')
    },
  },
  {
    method: 'post',
    path: '/v1/durations/batch-audit',
    handler: ({ body }) => {
      const ids = Array.isArray(body.ids) ? body.ids.map(Number) : []
      let done = 0
      ids.forEach((id) => {
        const item = durations.find((d) => d.id === id)
        if (!item || item.status !== 'PENDING_AUDIT') return
        item.status = body.action === 'APPROVE' ? 'APPROVED' : 'REJECTED'
        item.auditor = body.auditor || '赵慧敏'
        item.auditedAt = now()
        item.remark = body.action === 'APPROVE' ? '' : body.remark || '批量驳回'
        done += 1
      })
      return ok({ count: done }, `已处理 ${done} 条`)
    },
  },

  /* ---------- 审核统计（看板用） ---------- */
  {
    method: 'get',
    path: '/v1/duration-audits/summary',
    handler: () => {
      const pending = durations.filter((d) => d.status === 'PENDING_AUDIT').length
      return ok({ ...audit, pendingInQueue: pending })
    },
  },
]