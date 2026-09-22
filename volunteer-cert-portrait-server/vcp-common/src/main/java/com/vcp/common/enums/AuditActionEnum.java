package com.vcp.common.enums;

import lombok.Getter;

/**
 * 审核动作（流水表用）。
 *
 * <p>对应 {@code duration_audit.action} 字段，码值来源 {@code sys_dict} 中
 * {@code dict_type = 'audit_action'} 的字典项。库中存英文码，中文标签由前端查字典展示。
 *
 * <p><b>⚠️ 动作与状态不同形，切勿混用：</b>本枚举是「做了一次什么操作」，
 * 取值为 {@code SUBMIT / APPROVE / REJECT}（动词原形）；
 * 而 {@link DurationStatusEnum} 是「操作后处于什么状态」，取值为
 * {@code PENDING_SUBMIT / PENDING_AUDIT / APPROVED / REJECTED}（过去分词/形容词）。
 * 例如审核通过时，动作记 {@code APPROVE}，时长状态置 {@code APPROVED} —— 两者不可互相赋值。
 */
@Getter
public enum AuditActionEnum {

    /** 提交：组织管理员提交服务时长 */
    SUBMIT("SUBMIT", "提交"),
    /** 通过：学校管理员终审通过 */
    APPROVE("APPROVE", "通过"),
    /** 驳回：学校管理员终审驳回 */
    REJECT("REJECT", "驳回");

    /** 英文码，入库值 */
    private final String code;

    /** 中文标签，与 sys_dict.dict_value 保持一致 */
    private final String label;

    AuditActionEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 按英文码反查枚举。
     *
     * @param code 英文码，如 {@code APPROVE}
     * @return 匹配的枚举；无匹配或入参为 null 时返回 null
     */
    public static AuditActionEnum of(String code) {
        for (AuditActionEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}