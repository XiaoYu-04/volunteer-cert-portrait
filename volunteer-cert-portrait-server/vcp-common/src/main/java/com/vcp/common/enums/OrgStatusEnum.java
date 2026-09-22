package com.vcp.common.enums;

import lombok.Getter;

/**
 * 志愿组织审核状态。
 *
 * <p>对应 {@code org_info.status} 字段，码值来源 {@code sys_dict} 中
 * {@code dict_type = 'org_status'} 的字典项。库中存英文码，中文标签由前端查字典展示。
 *
 * <p>由学校管理员审核：只有 {@code APPROVED} 的组织才能发布活动。
 * 本枚举只表达「资质审核」这一维度，不含「停用/启用」（该功能是否保留见待办项 A10）。
 */
@Getter
public enum OrgStatusEnum {

    /** 待审核：组织已注册，等待学校管理员审核（建表默认值） */
    PENDING("PENDING", "待审核"),
    /** 已通过：资质审核通过，可发布活动 */
    APPROVED("APPROVED", "已通过"),
    /** 已驳回：资质审核未通过 */
    REJECTED("REJECTED", "已驳回");

    /** 英文码，入库值 */
    private final String code;

    /** 中文标签，与 sys_dict.dict_value 保持一致 */
    private final String label;

    OrgStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 按英文码反查枚举。
     *
     * @param code 英文码，如 {@code PENDING}
     * @return 匹配的枚举；无匹配或入参为 null 时返回 null
     */
    public static OrgStatusEnum of(String code) {
        for (OrgStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}