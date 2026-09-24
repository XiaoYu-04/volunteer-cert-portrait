package com.vcp.volunteer.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 活动图文说明中的一张图片。
 */
@Data
public class ActivityImageSaveDTO implements Serializable {

    private String fileUrl;

    private String fileName;

    private Long fileSize;

    private String contentType;

    /** 图片说明 */
    private String caption;
}
