package com.vcp.common.enums;

import lombok.Getter;

/**
 * 活动状态。
 *
 * <p>对应 {@code volunteer_activity.status} 字段，码值来源 {@code sys_dict} 中
 * {@code dict_type = 'activity_status'} 的字典项。库中存英文码，中文标签由前端查字典展示，
 * 本枚举的 label 只作为后端日志/兜底文案使用。
 */
@Getter
public enum ActivityStatusEnum {

    /** 草稿：组织管理员新建但尚未发布，学生不可见、不可报名 */
    DRAFT("DRAFT", "草稿"),
    /** 已发布：学生可报名，仅该状态允许报名 */
    PUBLISHED("PUBLISHED", "已发布"),
    /** 已结束：活动正常办完（与「已取消」不同，不表示未举办） */
    CLOSED("CLOSED", "已结束"),
    /** 已取消：活动未举办即终止 */
    CANCELED("CANCELED", "已取消");

    /** 英文码，入库值 */
    private final String code;

    /** 中文标签，与 sys_dict.dict_value 保持一致 */
    private final String label;

    ActivityStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 按英文码反查枚举。
     *
     * @param code 英文码，如 {@code PUBLISHED}
     * @return 匹配的枚举；无匹配或入参为 null 时返回 null
     */
    public static ActivityStatusEnum of(String code) {
        for (ActivityStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}