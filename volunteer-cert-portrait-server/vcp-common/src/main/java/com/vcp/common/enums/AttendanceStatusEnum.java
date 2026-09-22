package com.vcp.common.enums;

import lombok.Getter;

/**
 * 签到状态。
 *
 * <p>对应 {@code attendance_record.status} 字段，码值来源 {@code sys_dict} 中
 * {@code dict_type = 'attendance_status'} 的字典项。库中存英文码，中文标签由前端查字典展示。
 *
 * <p>状态流转：未签到 → 已签到 → 已签退；异常与缺勤是活动结束后的结论态。
 * 允许签到/签退的前置状态见 {@code ATTENDANCE_CANNOT_SIGN_IN} / {@code ATTENDANCE_CANNOT_SIGN_OUT}
 * 两个错误码的说明。
 */
@Getter
public enum AttendanceStatusEnum {

    /** 未签到：已生成签到记录但尚未签到 */
    NOT_SIGNED("NOT_SIGNED", "未签到"),
    /** 已签到：已签到、尚未签退 */
    SIGNED_IN("SIGNED_IN", "已签到"),
    /** 已签退：签到签退完整，是服务时长计算的正常依据 */
    SIGNED_OUT("SIGNED_OUT", "已签退"),
    /** 异常：签到签退时间不完整或超出允许窗口，需人工确认（具体判定规则见待办项 A2） */
    ABNORMAL("ABNORMAL", "异常"),
    /** 缺勤：活动结束仍未签到 */
    ABSENT("ABSENT", "缺勤");

    /** 英文码，入库值 */
    private final String code;

    /** 中文标签，与 sys_dict.dict_value 保持一致 */
    private final String label;

    AttendanceStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 按英文码反查枚举。
     *
     * @param code 英文码，如 {@code SIGNED_OUT}
     * @return 匹配的枚举；无匹配或入参为 null 时返回 null
     */
    public static AttendanceStatusEnum of(String code) {
        for (AttendanceStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}