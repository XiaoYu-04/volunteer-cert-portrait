package com.vcp.volunteer.mapper.row;

import lombok.Data;

import java.io.Serializable;

/**
 * 组织端概览的聚合计数，一条 SQL 全部取回。
 *
 * <p>六个指标卡 + 组织资料里的活动场次 / 签到率 / 通过率都靠这几个计数算出来，
 * 分成多条 count 查询会产生 8 次往返且口径容易写歪，这里用一条带标量子查询的
 * SELECT 一次算完（见 VolunteerActivityMapper.xml）。
 *
 * <p>计数口径：
 * <ul>
 *   <li>approvedSignupTotal 含 APPROVED 与 COMPLETED；rejectedSignupTotal 只算 REJECTED；
 *       通过率的分母不含 PENDING 与 CANCELED（待审的不该拉低通过率）。</li>
 *   <li>signedAttendanceTotal 含 SIGNED_IN / SIGNED_OUT / ABNORMAL（到了场就算签到），
 *       unsignedTotal 含 NOT_SIGNED 与 ABSENT。</li>
 * </ul>
 */
@Data
public class OrgOverviewRow implements Serializable {

    /** 本组织活动总场次 */
    private Long activityTotal;

    /** 进行中（PUBLISHED）的活动场次 */
    private Long ongoingTotal;

    /** 待审核的报名条数 */
    private Long pendingSignupTotal;

    /** 已通过（含已完成）的报名条数 */
    private Long approvedSignupTotal;

    /** 已驳回的报名条数 */
    private Long rejectedSignupTotal;

    /** 签到记录总数 */
    private Long attendanceTotal;

    /** 已签到人次 */
    private Long signedAttendanceTotal;

    /** 未签到人次（未签到 + 缺勤） */
    private Long unsignedTotal;
}
