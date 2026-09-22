package com.vcp.framework.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.vcp.common.exception.BusinessException;
import com.vcp.common.exception.ErrorCodeEnum;
import com.vcp.common.result.R;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器：把 Controller 层抛出的异常统一转成 {@link R} 外壳。
 *
 * <p><b>HTTP 状态码恒为 200，成败信息全部放在响应体的 {@code code} 字段里。</b>
 * 前端响应拦截器只读 body 的 code（非 0 即失败）并把 message 直接展示给用户，
 * 所以这里既不标 {@code @ResponseStatus}，也不继承 {@code ResponseEntityExceptionHandler}
 * ——后者会把参数校验异常映射成 400、把其它异常映射成 500，前端拿到的就不是统一外壳了。
 *
 * <p>日志分级原则——目的是不让系统缺陷被伪装成业务提示：
 * <ul>
 *   <li>可预期的错误（业务异常、未登录、无权限、参数校验失败）只回业务码，不记堆栈。
 *       其中业务异常额外记一条 warn：它通常意味着调用方误用或状态不满足，
 *       按业务码统计即可定位，堆栈对排查没有帮助。</li>
 *   <li>未预期的异常必须记 error 并打完整堆栈。这类异常对用户只呈现「系统繁忙」，
 *       堆栈是唯一的线索，缺了它就只能靠猜。</li>
 * </ul>
 *
 * <p>异常按类型分派而非按方法声明顺序：Spring 会挑参数类型最匹配的那个 handler，
 * 因此下面各分支互不干扰。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：业务代码主动抛出的可预期错误。
     *
     * <p>用 warn 且不打堆栈——文案本身已经说清了问题，堆栈只会淹没日志。
     *
     * @param e 业务异常
     * @return 携带异常自带业务码与文案的失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：code={}, message={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 未登录 / 登录已过期：由 Sa-Token 拦截器在鉴权失败时抛出。
     *
     * <p>返回 20001，前端拦截器命中该码会清除本地 token 并跳登录页。
     *
     * @param e 未登录异常
     * @return 认证失败响应
     */
    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLoginException(NotLoginException e) {
        return R.fail(ErrorCodeEnum.AUTH_FAILED);
    }

    /**
     * 角色不足：已登录但不具备接口要求的角色。
     *
     * @param e 角色校验异常
     * @return 无权限响应
     */
    @ExceptionHandler(NotRoleException.class)
    public R<Void> handleNotRoleException(NotRoleException e) {
        return R.fail(ErrorCodeEnum.NO_PERMISSION);
    }

    /**
     * 权限不足：已登录但不具备接口要求的权限标识。
     *
     * @param e 权限校验异常
     * @return 无权限响应
     */
    @ExceptionHandler(NotPermissionException.class)
    public R<Void> handleNotPermissionException(NotPermissionException e) {
        return R.fail(ErrorCodeEnum.NO_PERMISSION);
    }

    /**
     * 请求体参数校验失败：{@code @RequestBody} 上带 {@code @Valid} 时由 Spring 抛出。
     *
     * <p>{@code MethodArgumentNotValidException} 是 {@code BindException} 的子类，
     * 二者合并处理，参数按父类型接收。
     *
     * @param e 绑定校验异常
     * @return 参数错误响应，文案为「字段名 + 校验注解上的 message」
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleBindException(BindException e) {
        return R.fail(ErrorCodeEnum.PARAM_ERROR, joinFieldErrors(e.getBindingResult().getFieldErrors()));
    }

    /**
     * 方法参数校验失败：Controller 类上标 {@code @Validated} 时，
     * 对 {@code @RequestParam} / {@code @PathVariable} 的约束由该方法抛出。
     *
     * @param e 约束违反异常
     * @return 参数错误响应，文案为「字段名 + 校验注解上的 message」
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(violation -> lastNodeName(violation) + " " + violation.getMessage())
                .collect(Collectors.joining("；"));
        return R.fail(ErrorCodeEnum.PARAM_ERROR, message.isEmpty() ? ErrorCodeEnum.PARAM_ERROR.getMessage() : message);
    }

    /**
     * 请求体格式错误：JSON 语法错误、字段类型对不上（如给数字字段传了中文）等。
     *
     * @param e 消息不可读异常
     * @return 参数错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return R.fail(ErrorCodeEnum.PARAM_ERROR, "请求参数格式不正确");
    }

    /**
     * 路径没有对应接口：Spring 既找不到 Controller 也找不到静态资源时抛出。
     *
     * <p>单独接住而不落进兜底分支，是为了让「接口还没实现」与「系统出错」区分开 ——
     * 否则联调时两者都显示「系统繁忙」，白白浪费排查时间。
     *
     * @param e 资源未找到异常
     * @return 接口不存在响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("接口不存在：{}", e.getResourcePath());
        return R.fail(ErrorCodeEnum.NOT_FOUND);
    }

    /**
     * 兜底分支：所有未被上面分支接住的异常。
     *
     * <p>必须记 error 并打完整堆栈——对外只暴露「系统繁忙」，
     * 堆栈是排查的唯一入口。
     *
     * @param e 未预期的异常
     * @return 系统错误响应
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("未预期的异常", e);
        return R.fail(ErrorCodeEnum.SYSTEM_ERROR);
    }

    /**
     * 把字段校验失败项拼成一句可直接展示的提示，格式为「字段名 + message」。
     *
     * <p>不用 Spring 默认的 {@code getMessage()}：那是一整串带对象名、错误码的英文描述，
     * 既不适合给用户看，也会把内部类名暴露出去。
     *
     * @param fieldErrors 字段错误列表
     * @return 拼接后的提示；无字段错误时退回错误码默认文案
     */
    private String joinFieldErrors(List<FieldError> fieldErrors) {
        if (fieldErrors.isEmpty()) {
            return ErrorCodeEnum.PARAM_ERROR.getMessage();
        }
        return fieldErrors.stream()
                .map(error -> error.getField() + " " + textOrDefault(error.getDefaultMessage()))
                .collect(Collectors.joining("；"));
    }

    /**
     * 取校验路径的末级节点名。
     *
     * <p>{@code @RequestParam} 场景的路径形如 {@code list.pageNum}，
     * 直接输出会把方法名一起带上，取末级才是用户认得的字段名。
     *
     * @param violation 约束违反项
     * @return 字段名
     */
    private String lastNodeName(ConstraintViolation<?> violation) {
        String name = "";
        for (Path.Node node : violation.getPropertyPath()) {
            name = node.getName();
        }
        return name;
    }

    /**
     * 校验注解未写 message 时，{@code getDefaultMessage()} 可能为空，此时给个通用兜底。
     *
     * @param message 原始 message
     * @return 非空文案
     */
    private String textOrDefault(String message) {
        return message == null || message.isBlank() ? "校验未通过" : message;
    }
}