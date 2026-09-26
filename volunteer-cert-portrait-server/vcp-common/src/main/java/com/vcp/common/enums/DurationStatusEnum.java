package com.vcp.common.enums;

import lombok.Getter;

/**
 * 服务时长状态。
 *
 * <p>对应 {@code service_duration.status} 字段，码值来源 {@code sys_dict} 中
 * {@code dict_type = 'duration_status'} 的字典项。库中存英文码，中文标签由前端查字典展示。
 *
 * <p>状态流转：待提交 →（组织管理员提交）→ 待审核 →（学校管理员终审）→ 已通过 / 已驳回。
 * 只有 {@code APPROVED} 的时长才计入 {@code student_info.total_duration}。
 */
@Getter
public enum DurationStatusEnum {

    /** 待提交：活动结束、签到记录已生成，等待组织管理员提交实际时长 */
    PENDING_SUBMIT("PENDING_SUBMIT", "待提交"),
    /** 待审核：组织管理员已提交，等待学校管理员终审 */
    PENDING_AUDIT("PENDING_AUDIT", "待审核"),
    /** 已通过：终审通过，计入学生累计时长 */
    APPROVED("APPROVED", "已通过"),
    /** 已驳回：终审未通过，不计入累计时长 */
    REJECTED("REJECTED", "已驳回");

    /** 英文码，入库值 */
    private final String code;

    /** 中文标签，与 sys_dict.dict_value 保持一致 */
    private final String label;

    DurationStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

}