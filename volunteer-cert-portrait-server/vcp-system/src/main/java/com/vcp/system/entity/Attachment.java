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

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
