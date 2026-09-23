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
 * 签到签退记录实体，对应 attendance_record 表。
 *
 * <p>一条报名最多一条签到记录（{@code signup_id} 唯一）。记录在<b>报名审核通过时</b>生成，
 * 学生取消报名时把同一行重置为未签到，而不是删除 —— 删掉之后重新报名再通过，
 * 会撞 {@code signup_id} 唯一约束（与 A7 同一类坑）。
 *
 * <p><b>表里没有 hours 列</b>：实得时长由「签退 − 签到」实时算出，规则见
 * {@link com.vcp.volunteer.support.AttendancePolicy}。冗余存一列反而会在
 * 组织管理员手工修正签到时间后与时间字段漂移。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("attendance_record")
public class AttendanceRecord extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 报名ID → activity_signup.id（唯一） */
    private Long signupId;

    /** 签到时间 */
    private LocalDateTime signInTime;

    /** 签退时间 */
    private LocalDateTime signOutTime;

    /** 签到状态：NOT_SIGNED / SIGNED_IN / SIGNED_OUT / ABNORMAL / ABSENT */
    private String status;

    /** 备注（异常原因等） */
    private String remark;

    @TableLogic
    private Integer deleted;
}
