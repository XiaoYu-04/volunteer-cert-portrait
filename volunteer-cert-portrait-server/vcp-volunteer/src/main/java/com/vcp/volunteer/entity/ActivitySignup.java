package com.vcp.volunteer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vcp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 活动报名实体，对应 activity_signup 表。
 *
 * <p><b>取消报名复用同一行（待办 A7 的结论）</b>：表上有
 * {@code uk_activity_student(activity_id, student_id)} 唯一约束，若用软删除实现取消，
 * 同一学生重新报名会直接撞唯一键。因此取消只把 {@code status} 改成 CANCELED、
 * {@code deleted} 保持 0；重新报名时把同一行改回 PENDING 并刷新报名时间。
 *
 * <p>{@code student_id} 是 {@code student_info.id}（学生档案 id），<b>不是</b> {@code sys_user.id}。
 *
 * <p>{@code reason}（报名理由）列由 {@code sql/06} 补列脚本追加。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("activity_signup")
public class ActivitySignup extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动ID → volunteer_activity.id */
    private Long activityId;

    /** 学生档案ID → student_info.id */
    private Long studentId;

    /** 报名时间 */
    private LocalDateTime signupTime;

    /** 报名状态：PENDING / APPROVED / REJECTED / CANCELED / COMPLETED */
    private String status;

    /** 审核意见；驳回时为驳回理由，前端以「驳回理由」展示 */
    private String auditRemark;

    /** 审核人 → sys_user.id */
    private Long auditUserId;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 报名理由，学生提交报名时填写，可为空 */
    private String reason;

    @TableLogic
    private Integer deleted;
}
