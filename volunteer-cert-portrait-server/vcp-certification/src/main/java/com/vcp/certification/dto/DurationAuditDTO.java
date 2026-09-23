package com.vcp.certification.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 单条时长审核请求。
 *
 * <p>action 是审核动作，取 {@code APPROVE} / {@code REJECT}（动词原形），
 * <b>不是</b>状态值 {@code APPROVED} / {@code REJECTED} —— 两者不同形，见 AuditActionEnum。
 * 驳回时 remark 必填（前端也会先做一次非空校验，后端仍要自己挡，不能只依赖前端）。
 */
@Data
public class DurationAuditDTO implements Serializable {

    /** 审核动作：APPROVE 通过 / REJECT 驳回 */
    private String action;

    /** 审核备注；驳回时必填 */
    private String remark;
}
