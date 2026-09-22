package com.vcp.common.enums;

import lombok.Getter;

/**
 * 通知类型。
 *
 * <p>对应 {@code notification.type} 字段，码值来源 {@code sys_dict} 中
 * {@code dict_type = 'notification_type'} 的字典项。库中存英文码，中文标签由前端查字典展示。
 *
 * <p>类型决定前端点击通知后的跳转目标：报名结果进「我的报名」，时长审核进「我的时长」，
 * 系统公告不跳转。
 */
@Getter
public enum NotificationTypeEnum {

    /** 报名结果：报名被组织管理员审核（通过/驳回）时通知学生 */
    SIGNUP("SIGNUP", "报名结果"),
    /** 时长审核：时长被学校管理员终审（通过/驳回）时通知学生 */
    DURATION("DURATION", "时长审核"),
    /** 系统公告：学校管理员发布的全局通知 */
    SYSTEM("SYSTEM", "系统公告");

    /** 英文码，入库值 */
    private final String code;

    /** 中文标签，与 sys_dict.dict_value 保持一致 */
    private final String label;

    NotificationTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 按英文码反查枚举。
     *
     * @param code 英文码，如 {@code DURATION}
     * @return 匹配的枚举；无匹配或入参为 null 时返回 null
     */
    public static NotificationTypeEnum of(String code) {
        for (NotificationTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}