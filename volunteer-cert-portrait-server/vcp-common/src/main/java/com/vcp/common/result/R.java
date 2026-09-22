package com.vcp.common.result;

import com.vcp.common.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应外壳。
 *
 * <p>全项目所有接口的返回体统一为 {@code {code, message, data}}，成功时 {@code code = 0}。
 * 前端响应拦截器据此判定成败：非 0 一律视为失败，并把 {@code message} 直接展示给用户，
 * 因此 message 必须是可直接阅读的中文文案，不要塞英文、异常类名或堆栈信息。
 *
 * <p>认证类错误码（20001 认证失败 / 20002 账号停用 / 20003 无权限）在前端有特殊处理：
 * 命中即清除 token 并跳转登录页，这三个码不要复用于其他语义。
 *
 * @param <T> 业务数据类型
 */
@Data
public class R<T> implements Serializable {

    /** 成功码，与前端约定为 0 */
    public static final int SUCCESS = 0;

    /** 成功时的固定提示，前端不展示该字段 */
    public static final String SUCCESS_MESSAGE = "ok";

    private int code;

    private String message;

    private T data;

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功，不带数据。用于删除、审核一类不需要回传内容的操作。
     *
     * @param <T> 业务数据类型
     * @return 成功响应
     */
    public static <T> R<T> ok() {
        return new R<>(SUCCESS, SUCCESS_MESSAGE, null);
    }

    /**
     * 成功，携带业务数据。
     *
     * @param data 业务数据，允许为 null
     * @param <T>  业务数据类型
     * @return 成功响应
     */
    public static <T> R<T> ok(T data) {
        return new R<>(SUCCESS, SUCCESS_MESSAGE, data);
    }

    /**
     * 失败，使用错误码自带的文案。
     *
     * @param errorCode 错误码
     * @param <T>       业务数据类型
     * @return 失败响应
     */
    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 失败，用自定义文案覆盖错误码的默认文案。
     *
     * <p>用于需要拼接上下文的场景，例如「该分类下还有 3 场活动，无法删除」——
     * 错误码负责分类与定位，文案负责说清具体情况。
     *
     * @param errorCode 错误码
     * @param message   覆盖用的文案
     * @param <T>       业务数据类型
     * @return 失败响应
     */
    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return new R<>(errorCode.getCode(), message, null);
    }

    /**
     * 失败，直接指定码与文案。仅在错误码枚举覆盖不到时使用。
     *
     * @param code    错误码
     * @param message 提示文案
     * @param <T>     业务数据类型
     * @return 失败响应
     */
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }
}