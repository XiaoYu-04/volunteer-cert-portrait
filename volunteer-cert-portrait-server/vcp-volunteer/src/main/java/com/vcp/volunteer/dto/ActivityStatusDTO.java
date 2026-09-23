package com.vcp.volunteer.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 活动状态流转请求（PUT /api/v1/activities/{id}/status）。
 *
 * <p>前端「活动管理」页只提供三种流转：草稿→发布、已发布→结束、草稿/已发布→取消，
 * 具体允许的迁移由 Service 校验，这里只承载目标状态码。
 */
@Data
public class ActivityStatusDTO implements Serializable {

    /** 目标状态：PUBLISHED / CLOSED / CANCELED */
    private String status;
}
