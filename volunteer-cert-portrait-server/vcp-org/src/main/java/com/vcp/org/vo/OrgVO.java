package com.vcp.org.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 组织响应对象，字段名与前端契约保持一致。
 */
@Data
public class OrgVO implements Serializable {

    private Long id;

    private String name;

    private String code;

    private String contact;

    private String phone;

    private String email;

    private String college;

    private Integer memberCount;

    /** 格式化后的成立日期，避免前端直接渲染 ISO 日期 */
    private String foundedAt;

    private String status;

    private String intro;

    private Long activities;

    private BigDecimal signRate;

    private BigDecimal passRate;

    private String auditRemark;
}
