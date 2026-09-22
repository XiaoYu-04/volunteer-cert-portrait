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
 * 通知实体，对应 notification 表。
 *
 * <p><b>为什么按收件人一行</b>：is_read 是"这一行被这个人读了"，天然按人存。
 * 一条群发公告因此插入 N 行（每个收件人一行），并用 batchNo 串起来 ——
 * 管理员删除公告时只有自己那一行的 id，必须靠批次号整批删除，
 * 否则删掉的只是自己的一份，学生端仍然看得到。
 *
 * <p>通知不做逻辑删除，也没有 update_time，故不继承 BaseEntity。
 */
@Data
@TableName("notification")
public class Notification implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人，指向 sys_user.id */
    private Long userId;

    private String title;

    private String content;

    /** 通知类型：SIGNUP / DURATION / SYSTEM，见 NotificationTypeEnum */
    private String type;

    /** 是否已读 */
    private Boolean isRead;

    /**
     * 通知来源。前端字段名叫 from，但那是 SQL 关键字，
     * 列名只能取 source，出参时再映射回 from（见 NotificationVO）。
     */
    private String source;

    /** 是否置顶。列名 is_top，故必须显式声明列名，否则会按字段名找 top 列而报错 */
    @TableField("is_top")
    private Boolean top;

    /** 群发批次号；非群发的通知为 null */
    private String batchNo;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
