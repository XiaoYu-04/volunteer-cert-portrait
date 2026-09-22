package com.vcp.system.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 启停用账号请求体（PUT /api/v1/system/users/{id}/status）。
 *
 * <p>取值是前端口径的 ACTIVE / DISABLED 字符串，
 * 由 Service 经 UserStatusEnum 翻成数据库的 1 / 0。
 */
@Data
public class StatusUpdateDTO implements Serializable {

    /** ACTIVE 启用 / DISABLED 停用 */
    private String status;
}
