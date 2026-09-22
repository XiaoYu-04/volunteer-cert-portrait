package com.vcp.common.exception;

/**
 * 错误码契约。
 *
 * <p>实现类负责给出码值与默认文案。业务异常 {@link BusinessException} 与统一返回
 * {@code R.fail()} 都依赖本接口，因此新增错误码时应实现本接口（推荐直接用
 * {@link ErrorCodeEnum}），而不是散落硬编码数字。
 *
 * <p>码值分段约定（前端已按此分段硬编码，不要越段使用）：
 * <ul>
 *   <li>0 成功</li>
 *   <li>1xxxx 通用（参数、唯一性、资源不存在、系统错误）</li>
 *   <li>2xxxx 认证与权限</li>
 *   <li>3xxxx 活动与报名</li>
 *   <li>4xxxx 时长认证</li>
 *   <li>5xxxx 公益画像与统计</li>
 * </ul>
 */
public interface ErrorCode {

    /**
     * 错误码。
     *
     * @return 码值
     */
    int getCode();

    /**
     * 默认提示文案，需可直接展示给用户。
     *
     * @return 中文文案
     */
    String getMessage();
}