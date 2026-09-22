package com.vcp.common.enums;

import lombok.Getter;

/**
 * 报名状态。
 *
 * <p>对应 {@code activity_signup.status} 字段，码值来源 {@code sys_dict} 中
 * {@code dict_type = 'signup_status'} 的字典项。库中存英文码，中文标签由前端查字典展示。
 *
 * <p>注意与 {@link AttendanceStatusEnum} 的分工：本枚举描述「报名审核」这一层，
 * 学生是否真的到场签到由签到状态表达。
 */
@Getter
public enum SignupStatusEnum {

    /** 待审核：学生已提交报名，等待组织管理员审核 */
    PENDING("PENDING", "待审核"),
    /** 已通过：审核通过，可参加活动（签到记录在此状态下产生） */
    APPROVED("APPROVED", "已通过"),
    /** 已驳回：审核未通过 */
    REJECTED("REJECTED", "已驳回"),
    /** 已取消：学生或组织取消报名 */
    CANCELED("CANCELED", "已取消"),
    /** 已完成：活动结束且已参加，是画像统计「参与活动次数」的口径 */
    COMPLETED("COMPLETED", "已完成");

    /** 英文码，入库值 */
    private final String code;

    /** 中文标签，与 sys_dict.dict_value 保持一致 */
    private final String label;

    SignupStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 按英文码反查枚举。
     *
     * @param code 英文码，如 {@code APPROVED}
     * @return 匹配的枚举；无匹配或入参为 null 时返回 null
     */
    public static SignupStatusEnum of(String code) {
        for (SignupStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}