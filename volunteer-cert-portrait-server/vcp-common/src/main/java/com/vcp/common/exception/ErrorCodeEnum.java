package com.vcp.common.exception;

/**
 * 全局错误码。
 *
 * <p>码值与文案是**从前端 mock 层反推并冻结的契约**：前端已经把码值硬编码在
 * 响应拦截器与各页面的判断里（尤其 20001/20002/20003 命中即清 token 跳登录），
 * 因此这里的码值不可随意改动，新增只能往后加。
 *
 * <p>文案是给用户看的默认值。同一码在不同场景需要更具体的说法时，
 * 用 {@code R.fail(码, "具体文案")} 覆盖，不要为此新增码值 ——
 * 例如 10001 既覆盖参数格式错误也覆盖唯一性冲突，靠文案区分。
 */
public enum ErrorCodeEnum implements ErrorCode {

    // ==================== 1xxxx 通用 ====================

    /** 未预期的异常兜底，由全局异常处理器使用，不主动抛出 */
    SYSTEM_ERROR(10000, "系统繁忙，请稍后重试"),
    /** 参数格式、必填、唯一性等校验失败 */
    PARAM_ERROR(10001, "参数不合法"),
    USER_NOT_FOUND(10002, "用户不存在"),
    STUDENT_NOT_FOUND(10003, "学生档案不存在"),
    NOTIFICATION_NOT_FOUND(10004, "通知不存在"),
    ORG_NOT_FOUND(10005, "组织不存在"),
    ORG_ALREADY_AUDITED(10006, "该组织已审核，无法重复操作"),
    /**
     * 请求的路径没有对应接口（Controller 不存在）。由全局异常处理器使用，业务代码不主动抛。
     * 与上面几个「XX 不存在」不同：那些指业务数据查不到，这个指接口本身没实现。
     */
    NOT_FOUND(10007, "请求的接口不存在"),

    // ==================== 2xxxx 认证与权限 ====================

    /**
     * 认证失败：既用于「用户名或密码错误」，也用于「未登录 / 登录已过期」。
     * 默认文案取后者（拦截器场景更常见），登录接口处用自定义文案覆盖。
     */
    AUTH_FAILED(20001, "登录已过期，请重新登录"),
    ACCOUNT_DISABLED(20002, "账号已停用，请联系学校管理员"),
    /** 已登录但角色/权限不足 */
    NO_PERMISSION(20003, "没有操作权限"),
    /**
     * 登录连续失败次数超限，账号被临时锁定。
     *
     * <p>默认文案是没带剩余时间时的兜底；登录接口一律用自定义文案覆盖，
     * 带上剩余分钟数——「请 3 分钟后再试」用户会等，「请稍后再试」用户会一直点，
     * 反而把锁定期限不断续上。
     *
     * <p>码值刻意不放进前端 {@code utils/request.js} 的
     * {@code UNAUTHORIZED_CODES = [20001, 20002, 20003]}：命中那三个码前端会清 token
     * 跳登录页，而"账号被锁"本身发生在登录页，需要留在原地把文案显示出来。
     */
    ACCOUNT_LOCKED(20004, "账号已被锁定，请稍后再试"),
    /**
     * 本人修改密码时原密码不正确。
     *
     * <p>同样必须避开 UNAUTHORIZED_CODES：原密码输错只是这一次操作失败，
     * 当前登录态依然有效，把用户踢回登录页属于误伤。
     */
    OLD_PASSWORD_ERROR(20005, "原密码不正确"),

    // ==================== 3xxxx 活动与报名 ====================

    ACTIVITY_NOT_FOUND(30001, "活动不存在"),
    CATEGORY_NAME_EXISTS(30002, "分类名称已存在"),
    CATEGORY_NOT_FOUND(30003, "分类不存在"),
    /** 分类下仍有活动时拒绝删除，文案需带上具体场次，用自定义文案覆盖 */
    CATEGORY_IN_USE(30004, "该分类下还有活动，无法删除"),
    /** 活动非 PUBLISHED 状态 */
    ACTIVITY_NOT_OPEN(30005, "该活动当前不接受报名"),
    ACTIVITY_FULL(30006, "名额已满"),
    SIGNUP_DUPLICATE(30007, "你已报名该活动，请勿重复提交"),
    SIGNUP_NOT_FOUND(30008, "报名记录不存在"),
    SIGNUP_ALREADY_AUDITED(30009, "该报名已审核，无法重复操作"),
    SIGNUP_COMPLETED(30010, "已完成的活动无法取消"),
    ATTENDANCE_NOT_FOUND(30011, "签到记录不存在"),
    /** 仅 NOT_SIGNED / ABSENT 可签到 */
    ATTENDANCE_CANNOT_SIGN_IN(30012, "当前状态无法签到"),
    /** 仅 SIGNED_IN 可签退 */
    ATTENDANCE_CANNOT_SIGN_OUT(30013, "请先签到再签退"),

    // ==================== 4xxxx 时长认证 ====================

    DURATION_NOT_FOUND(40001, "时长记录不存在"),
    DURATION_ALREADY_AUDITED(40002, "该记录已审核，无法重复操作"),

    // ==================== 5xxxx 公益画像与统计 ====================

    PORTRAIT_NOT_GENERATED(50001, "画像尚未生成");

    private final int code;

    private final String message;

    ErrorCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

}
