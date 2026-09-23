package com.vcp.certification.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vcp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 服务时长实体，对应 service_duration 表。
 *
 * <p><b>一条报名最多一条时长记录</b>：{@code signup_id} 是 NOT NULL UNIQUE，而前端提交时
 * 只给 studentId + activityId，因此 signup_id 由服务端反查（见 {@code DurationRefMapper}）。
 * 这也决定了「驳回后重新提交」只能更新同一行而不是插新行 —— 否则直接撞唯一约束。
 *
 * <p><b>三个审核字段声明 ALWAYS 更新策略，是有意为之</b>：重新提交时要清空上一轮的
 * 审核人 / 审核时间 / 驳回理由（那一次的结论已经失效，历史仍完整留在 duration_audit
 * 流水里）。MyBatis-Plus 默认策略是「null 字段不进 SET 子句」，不显式声明就永远清不掉，
 * 表现为「重新提交后列表里还挂着上次的驳回理由」这种不报错的错。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("service_duration")
public class ServiceDuration extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 报名ID → activity_signup.id（唯一，一条报名对应一条时长记录） */
    private Long signupId;

    /** 活动ID → volunteer_activity.id（冗余，便于按活动统计） */
    private Long activityId;

    /** 学生档案ID → student_info.id（冗余，便于按学生/学院统计） */
    private Long studentId;

    /** 实际服务时长（小时），NUMERIC(10,1) */
    private BigDecimal duration;

    /** 状态：PENDING_SUBMIT / PENDING_AUDIT / APPROVED / REJECTED，见 DurationStatusEnum */
    private String status;

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** 审核人 → sys_user.id（学校管理员）；重新提交时清空 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long auditUserId;

    /** 审核时间；重新提交时清空 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime auditTime;

    /** 审核意见（驳回原因）；重新提交时清空 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String auditRemark;

    /** 逻辑删除：0 未删除 / 1 已删除 */
    @TableLogic
    private Integer deleted;

    /** 证明材料路径/URL */
    private String proof;

    /** 所属组织 → org_info.id；提交时按活动归属写入，用于组织端数据范围过滤 */
    private Long orgId;

    /** 活动类型（分类名，如「社区服务」）；提交时按活动分类写入，供画像标签统计 */
    private String activityType;
}
