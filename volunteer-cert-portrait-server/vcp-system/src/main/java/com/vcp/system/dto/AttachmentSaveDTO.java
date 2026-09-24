package com.vcp.system.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 业务侧提交的附件元数据。
 *
 * <p>文件本体已经由上传接口写入磁盘，本对象只负责把访问地址、大小、说明和排序
 * 交给业务 Service 落库。这样活动事务失败时不会留下 attachment 关联行。
 */
@Data
public class AttachmentSaveDTO implements Serializable {

    /** 访问地址，例如 /uploads/activities/2026/09/xxx.png */
    private String fileUrl;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    private String contentType;

    /** 图片说明 */
    private String caption;
}
