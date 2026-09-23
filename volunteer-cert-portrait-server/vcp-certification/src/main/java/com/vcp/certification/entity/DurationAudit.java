package com.vcp.certification.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 时长审核流水实体，对应 duration_audit 表。
 *
 * <p>流水按「只追加」设计：没有 deleted、没有 update_time，因此不继承 BaseEntity，
 * 只保留 create_time（由 MetaObjectHandler 在 INSERT 时填充）。
 *
 * <p><b>action 与状态不同形</b>：本表存的是「做了一次什么操作」——
 * SUBMIT / APPROVE / REJECT（动词原形），而 service_duration.status 存的是
 * 「操作后处于什么状态」—— PENDING_AUDIT / APPROVED / REJECTED。
 * 两者不可互相赋值，见 {@code AuditActionEnum} 的说明。
 *
 * <p>这张表是「驳回 → 重新提交 → 再审核」这条链路的唯一凭据：
 * service_duration 每次重新提交都会把上一轮的审核人 / 时间 / 理由清空，
 * 只在这张流水里留有完整轨迹。
 */
@Data
@TableName("duration_audit")
public class DurationAudit implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 时长记录ID → service_duration.id */
    private Long durationId;

    /** 操作人 → sys_user.id：提交时是组织管理员，审核时是学校管理员 */
    private Long auditorId;

    /** 动作：SUBMIT / APPROVE / REJECT，见 AuditActionEnum */
    private String action;

    /** 备注：驳回时存驳回理由 */
    private String remark;

    /** 发生时间，INSERT 时由 MetaObjectHandler 填充 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
