package com.vcp.common.enums;

import lombok.Getter;

/**
 * 角色码。
 *
 * <p>对应 {@code sys_role.role_code} 字段（唯一约束），是权限判定与 Sa-Token 角色校验的依据。
 *
 * <p>本枚举是特例：<b>不在 {@code sys_dict} 里</b>，没有对应的 {@code dict_type}。
 * 角色名（学生/组织管理员/学校管理员）存在 {@code sys_role.role_name}，
 * 由种子数据写入，本枚举的 label 与之保持一致。
 */
@Getter
public enum RoleCodeEnum {

    /** 学生：报名活动、查看本人时长与公益画像 */
    STUDENT("STUDENT", "学生"),
    /** 组织管理员：发布活动、审核报名、提交服务时长 */
    ORG_ADMIN("ORG_ADMIN", "组织管理员"),
    /** 学校管理员：审核组织资质、终审服务时长、查看数据看板 */
    SCHOOL_ADMIN("SCHOOL_ADMIN", "学校管理员");

    /** 英文码，入库值 */
    private final String code;

    /** 中文标签，与 sys_role.role_name 保持一致 */
    private final String label;

    RoleCodeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 按英文码反查枚举。
     *
     * @param code 英文码，如 {@code ORG_ADMIN}
     * @return 匹配的枚举；无匹配或入参为 null 时返回 null
     */
    public static RoleCodeEnum of(String code) {
        for (RoleCodeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}