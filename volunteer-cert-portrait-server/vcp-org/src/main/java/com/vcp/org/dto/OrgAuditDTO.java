package com.vcp.org.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 组织资质审核请求。
 */
@Data
public class OrgAuditDTO implements Serializable {

    /** 审核动作：APPROVE / REJECT */
    private String action;

    /** 审核备注；驳回时必填 */
    private String remark;
}
