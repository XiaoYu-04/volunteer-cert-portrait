package com.vcp.common.exception;

/**
 * 业务异常。
 *
 * <p>业务代码遇到**可预期**的错误时抛出本异常（参数不合法、状态不允许、资源不存在等），
 * 由 vcp-framework 的全局异常处理器统一转成 {@code R.fail}，Controller 里不必逐个 try-catch。
 *
 * <p>不要用它包装不可预期的异常（如 NullPointerException）——那些应落到全局处理器的
 * 兜底分支并记日志，否则系统缺陷会被伪装成业务提示，排查时看不到堆栈。
 */
public class BusinessException extends RuntimeException {

    /** 错误码，对应 {@link ErrorCode#getCode()} */
    private final int code;

    /**
     * 使用错误码自带的默认文案。
     *
     * @param errorCode 错误码
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 用自定义文案覆盖默认文案，用于需要拼接上下文的场景。
     *
     * @param errorCode 错误码
     * @param message   展示给用户的文案
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    /**
     * 错误码。
     *
     * @return 码值
     */
    public int getCode() {
        return code;
    }
}