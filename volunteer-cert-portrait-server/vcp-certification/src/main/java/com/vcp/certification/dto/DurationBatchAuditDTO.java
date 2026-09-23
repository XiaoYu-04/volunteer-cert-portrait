package com.vcp.certification.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量审核请求，对应前端「全选本页待审核记录 → 批量通过」。
 *
 * <p>与单条审核共用 action / remark 的语义：驳回时 remark 必填。
 * ids 里已审核过的记录会被跳过（不报错），返回值里的 count 是实际处理条数。
 */
@Data
public class DurationBatchAuditDTO implements Serializable {

    /** 待处理的时长记录 id 列表 */
    private List<Long> ids;

    /** 审核动作：APPROVE 通过 / REJECT 驳回 */
    private String action;

    /** 审核备注；批量驳回时必填 */
    private String remark;
}
