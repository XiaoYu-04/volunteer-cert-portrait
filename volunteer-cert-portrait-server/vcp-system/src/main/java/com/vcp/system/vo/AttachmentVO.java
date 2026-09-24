package com.vcp.system.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 附件响应对象。
 */
@Data
public class AttachmentVO implements Serializable {

    private Long id;

    private String fileUrl;

    private String fileName;

    private Long fileSize;

    private String contentType;

    private String caption;

    private Integer sortOrder;
}
