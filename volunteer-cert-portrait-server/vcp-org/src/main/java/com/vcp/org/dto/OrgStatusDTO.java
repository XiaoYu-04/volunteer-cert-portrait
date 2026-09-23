package com.vcp.org.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 组织启停状态请求。
 */
@Data
public class OrgStatusDTO implements Serializable {

    /** 目标状态：APPROVED / DISABLED */
    private String status;
}
