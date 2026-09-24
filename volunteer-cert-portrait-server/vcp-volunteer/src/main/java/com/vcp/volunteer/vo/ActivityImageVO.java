package com.vcp.volunteer.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 活动详情中的图文说明图片。
 */
@Data
public class ActivityImageVO implements Serializable {

    private Long id;

    private String fileUrl;

    private String fileName;

    private Long fileSize;

    private String contentType;

    private String caption;

    private Integer sortOrder;
}
