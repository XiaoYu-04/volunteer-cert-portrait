package com.vcp.common.enums;

import lombok.Getter;

/**
 * 用户状态。
 *
 * <p>对应 {@code sys_user.status} 字段。本枚举是特例：<b>不在 {@code sys_dict} 里</b>，
 * 且数据库中存的是 {@code SMALLINT}（1 启用 / 0 禁用）而非英文码，
 * 而前端已按 {@code ACTIVE} / {@code DISABLED} 字符串使用。
 *
 * <p>因此本枚举同时携带英文码（枚举名，与前端口径一致）与数据库值（{@code dbValue}）：
 * 写入数据库前用 {@link #getDbValue()} 取值，从数据库读出后用 {@link #ofDbValue(Integer)} 还原，
 * 出参给前端时用 {@link #getCode()}。这样两边都不用改。
 *
 * <p>对应待办项 A9（{@code sys_user.status} 用什么类型，尚未拍板）：
 * 建议最终改为与全项目一致的「英文码 + sys_dict 字典」，
 * 届时删掉 {@code dbValue} 与 {@link #ofDbValue(Integer)} 即可，调用方不受影响。
 */
@Getter
public enum UserStatusEnum {

    /** 正常：可登录，数据库值 1 */
    ACTIVE(1, "正常"),
    /** 已停用：禁止登录（登录时报 ACCOUNT_DISABLED），数据库值 0 */
    DISABLED(0, "已停用");

    /** 数据库存储值，对应 sys_user.status 的 SMALLINT */
    private final Integer dbValue;

    /** 中文标签 */
    private final String label;

    UserStatusEnum(Integer dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    /**
     * 取英文码，与前端使用的字符串口径一致。
     *
     * <p>本枚举没有独立的 code 字段，英文码即枚举名（{@code ACTIVE} / {@code DISABLED}）。
     *
     * @return 英文码
     */
    public String getCode() {
        return name();
    }

    /**
     * 按英文码反查枚举。
     *
     * @param code 英文码，如 {@code ACTIVE}
     * @return 匹配的枚举；无匹配或入参为 null 时返回 null
     */
    public static UserStatusEnum of(String code) {
        for (UserStatusEnum item : values()) {
            if (item.name().equals(code)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 按数据库值反查枚举。
     *
     * @param dbValue 数据库值，1 启用 / 0 禁用
     * @return 匹配的枚举；无匹配或入参为 null 时返回 null
     */
    public static UserStatusEnum ofDbValue(Integer dbValue) {
        for (UserStatusEnum item : values()) {
            if (item.dbValue.equals(dbValue)) {
                return item;
            }
        }
        return null;
    }
}