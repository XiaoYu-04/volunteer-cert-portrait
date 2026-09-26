package com.vcp.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 附件实体，对应 attachment 表。
 *
 * <p>bizId 是多态外键（指向不同业务表），因此没有物理外键约束，
 * 删除业务数据时附件不会自动清理，需要业务侧自行处理。
 *
 * <p>2026-09-27 起图片本体以 bytea 存 {@code file_data}，磁盘不再保留图片文件；
 * 读取走 {@code GET /api/v1/attachments/{id}/content}。
 *
 * <p>附件只追加不修改，没有 update_time 与 deleted，故不继承 BaseEntity。
 */
@Data
@TableName("attachment")
public class Attachment implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务类型：ACTIVITY / ORG / AVATAR，见 AttachmentBizTypeEnum */
    private String bizType;

    /** 业务ID（多态，无外键） */
    private Long bizId;

    /** 原始文件名 */
    private String fileName;

    /** 访问地址 */
    private String fileUrl;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型，如 image/png */
    private String contentType;

    /** 图片说明 */
    private String caption;

    /** 同一业务下的展示顺序，从 0 开始 */
    private Integer sortOrder;

    /**
     * 图片二进制内容（bytea）。
     *
     * <p>{@code select = false}：普通列表/详情查询不带上这个 TOAST 大字段，
     * 只有读取接口按 id 单查时显式取；否则每次查活动列表都会把几百 KB 的字节
     * 从数据库拖回来再被丢弃。
     */
    @TableField(value = "file_data", select = false)
    private byte[] fileData;

    /** 文件内容 SHA-256（十六进制小写），读取接口用作 HTTP ETag */
    private String sha256;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
