package com.vcp.certification.service;

import com.vcp.certification.dto.DurationAuditDTO;
import com.vcp.certification.dto.DurationBatchAuditDTO;
import com.vcp.certification.vo.DurationAuditSummaryVO;

/**
 * 服务时长审核服务（学校管理员）。
 *
 * <p>状态机：PENDING_AUDIT --审核--> APPROVED / REJECTED。
 * 只有 APPROVED 的时长才计入 student_info.total_duration；每次审核都会往 duration_audit
 * 写一条流水，并给学生落一条 DURATION 类型通知。
 */
public interface DurationAuditService {

    /**
     * 审核单条服务时长。
     *
     * @param id  时长记录 id
     * @param dto 审核动作（APPROVE / REJECT）与备注；驳回时备注必填
     * @throws com.vcp.common.exception.BusinessException 记录不存在（40001）、已审核（40002）、动作或备注不合法（10001）
     */
    void audit(Long id, DurationAuditDTO dto);

    /**
     * 批量审核。
     *
     * <p>已是终态（APPROVED / REJECTED）或已被删除的记录会被跳过而不报错 ——
     * 批量操作面向的是「当前页待审核」，列表可能已经过期。
     *
     * @param dto 记录 id 列表、审核动作与备注
     * @return 实际处理的条数
     * @throws com.vcp.common.exception.BusinessException id 列表为空、动作或备注不合法（10001）
     */
    int batchAudit(DurationBatchAuditDTO dto);

    /**
     * 取审核三态统计（全校口径，审核页顶部指标卡用）。
     *
     * @return 总量、通过率与三态分布
     */
    DurationAuditSummaryVO getSummary();
}
