package com.vcp.volunteer.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 报名审核请求（PUT /api/v1/signups/{id}/audit）。
 */
@Data
public class SignupAuditDTO implements Serializable {

    /** 审核动作：APPROVE / REJECT（注意与状态码 APPROVED / REJECTED 不同形） */
    private String action;

    /** 审核备注；驳回时必填，会随通知一起发给学生 */
    private String remark;
}
