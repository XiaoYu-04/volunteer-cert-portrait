package com.vcp.framework.log;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
// Spring Boot 4 已迁移到 Jackson 3（包名 tools.jackson，非受检异常）。
// 工程 classpath 里的 com.fasterxml.jackson 是 Knife4j 传递引入的库，
// 容器不会为它注册 ObjectMapper Bean —— 注入它会启动失败。
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志切面：拦截标注了 {@link OperationLog} 的方法，采集一条日志并以事件形式抛出。
 *
 * <p><b>为什么切面不直接写数据库</b>：{@code operation_log} 表归属 vcp-system 域，而本类所在模块
 * 的依赖方向是 {@code vcp-common → vcp-framework → vcp-system}，vcp-framework <b>不能反向依赖</b>
 * vcp-system，因此碰不到该表的实体与 Mapper。按项目约定「通知类耦合用 Spring Event 解耦」，
 * 切面只调用 {@link ApplicationEventPublisher#publishEvent} 发布
 * {@link OperationLogEvent}，由 vcp-system 侧的监听器负责落库。
 * 这也意味着<b>切面本身不感知日志是否入库成功</b>，落库失败只会在监听器里体现。
 *
 * <p><b>通知方式选 {@code @Around} 而非 {@code @AfterReturning} + {@code @AfterThrowing}</b>：
 * <ul>
 *   <li>耗时只能在环绕通知里自然测得 —— 后置/异常通知都拿不到"方法开始时刻"，
 *       要么把开始时间塞进 ThreadLocal（还得额外清理，多一处出错点），要么再拆一个前置通知；</li>
 *   <li>成功与异常两条路径共用同一段采集逻辑，不必写两遍；</li>
 *   <li>返回值和异常都原样透传（{@code return joinPoint.proceed()} + {@code finally}），
 *       对业务方法完全透明。</li>
 * </ul>
 * 代价是"方法抛异常时也会记一条日志"。这是刻意的取舍：宁可有记录、不可漏记录。
 * B16 扩表后 {@code operation_log} 已有 {@code result} 列，本类据业务方法是否正常返回
 * 写入 {@code SUCCESS} / {@code FAIL}，成功与失败因此可区分。
 *
 * <p><b>必须防住的坑</b>（改动本类前务必读一遍）：
 * <ol>
 *   <li><b>参数不能直接序列化</b>：{@code HttpServletRequest} / {@code HttpServletResponse}
 *       （以及它们的父接口 {@code ServletRequest}/{@code ServletResponse}）、
 *       {@code MultipartFile} 都不是可 JSON 序列化的数据对象，
 *       硬序列化会抛异常甚至把请求体读空（上传接口尤其致命）。这些参数必须先过滤掉。</li>
 *   <li><b>采集失败不能影响业务</b>：整个采集过程包在 try-catch 里，失败只记 warn。
 *       日志是旁路功能，不能因为一次 JSON 序列化失败就让正常的业务请求报 500。</li>
 *   <li><b>切面自身绝不能抛异常</b>：本切面在 {@code finally} 中采集，此刻若业务方法正在抛异常，
 *       采集再抛出就会把原始业务异常<b>顶替掉</b>，异常处理链路会拿到错误的异常。
 *       所以采集代码的 try-catch 是必需的，不是防御性编程。</li>
 * </ol>
 *
 * <p>尚未处理（不阻塞，留给后续）：{@code params} 的脱敏与超长截断 —— 表注释里也写了
 * "建议脱敏后再落库"。当前实现原样序列化，密码类字段若出现在参数里会被明文记录，
 * 应在 B16 一并处理。
 *
 * <p><b>为什么这里直接用 {@link StpUtil} 而不走 {@code AuthUtils}</b>：项目约定业务代码取登录态
 * 走 {@code com.vcp.framework.security.AuthUtils}，但该类在未登录时会抛
 * {@code BusinessException}（刻意的，避免把"没登录"伪装成"查不到数据"）。本切面恰好相反 ——
 * 未登录是<b>正常情况</b>（登录接口、开放接口都会走到这里），而且切面一旦抛异常就会
 * 连带丢掉整条日志、甚至顶替业务异常。所以这里显式用 {@code StpUtil.isLogin()} 做前置判断，
 * 未登录时留空。这是切面对 AuthUtils 约定的<b>有意例外</b>，不是漏改。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    /**
     * Sa-Token 会话中可能存放用户名的属性键。
     *
     * <p>注意：登录约定（见 {@code AuthUtils} 类注释）只往会话里写了 roleCode / orgId / studentId，
     * <b>没有 username</b>，所以这里读不到是常态、不是 bug。username 的权威来源是
     * {@code sys_user.username}，由 vcp-system 侧的落库监听器按 {@code userId} 反查回填。
     * 保留这次读取是为了：万一登录接口顺手把用户名也写进会话，就能省掉一次查库。
     */
    private static final String SESSION_KEY_USERNAME = "username";

    /** 模块与动作之间的分隔符，拼成入库的 operation 字段 */
    private static final String OPERATION_SEPARATOR = " - ";

    /** 执行结果：业务方法正常返回 */
    private static final String RESULT_SUCCESS = "SUCCESS";

    /** 执行结果：业务方法抛出异常 */
    private static final String RESULT_FAIL = "FAIL";

    private final ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper;

    /**
     * 环绕通知：放行原方法，并在其返回或抛异常后采集操作日志。
     *
     * @param joinPoint    连接点
     * @param operationLog 方法上的操作日志注解，由切点表达式绑定
     * @return 原方法的返回值，原样透传
     * @throws Throwable 原方法抛出的异常，原样透传
     */
    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        // 先置失败，方法正常返回后再置成功：异常路径上 finally 先于异常向外传播执行，
        // 此刻 success 仍为 false，正好判定为失败，不需要额外捕获异常。
        boolean success = false;
        try {
            Object result = joinPoint.proceed();
            success = true;
            return result;
        } finally {
            // 放 finally：成功与异常两条路径都要留痕。此处绝不能抛出异常，否则会顶替业务异常。
            collect(joinPoint, operationLog, System.currentTimeMillis() - start, success);
        }
    }

    /**
     * 采集一条操作日志并发布事件；任何失败都只记 warn，不影响业务方法。
     *
     * @param joinPoint    连接点
     * @param operationLog 操作日志注解
     * @param cost         业务方法耗时（毫秒）
     * @param success      业务方法是否正常返回
     */
    private void collect(ProceedingJoinPoint joinPoint, OperationLog operationLog, long cost, boolean success) {
        try {
            String module = operationLog.module();
            String action = operationLog.action();
            String operation = module + OPERATION_SEPARATOR + action;
            String method = joinPoint.getSignature().toShortString();
            // 注解显式关掉参数采集时不落 params：登录、注册的请求体里有明文密码，
            // 序列化进库等于把全站口令永久留痕（见 OperationLog#params 的说明）。
            String params = operationLog.params() ? toJson(joinPoint.getArgs()) : "";
            String ip = currentIp();

            // 未登录时留空，不抛异常：开放接口与登录接口也会走这里。
            // 注意 StpUtil 依赖请求上下文（内部走 RequestContextHolder），非 Web 线程调用时
            // 会抛 IllegalStateException —— 由最外层 try-catch 兜住，代价是这条日志整体丢失。
            // 本切面只标在 Controller 上，实际都在请求线程内，不会踩到。
            Long userId = null;
            String username = "";
            if (StpUtil.isLogin()) {
                userId = StpUtil.getLoginIdAsLong();
                username = currentUsername();
            }

            eventPublisher.publishEvent(new OperationLogEvent(this, userId, username, operation,
                    module, action, success ? RESULT_SUCCESS : RESULT_FAIL,
                    method, params, ip, LocalDateTime.now(), cost));
        } catch (Exception e) {
            log.warn("[操作日志] 采集失败，已忽略，不影响业务方法。method={}",
                    joinPoint.getSignature().toShortString(), e);
        }
    }

    /**
     * 把请求参数序列化为 JSON，过滤掉不可序列化的 Web 对象。
     *
     * @param args 方法参数数组
     * @return JSON 串；无参数或参数全被过滤时返回空串
     * @throws tools.jackson.core.JacksonException 序列化失败时抛出（Jackson 3 起为非受检），由调用方统一兜住
     */
    private String toJson(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        List<Object> serializable = new ArrayList<>(args.length);
        for (Object arg : args) {
            // 注意用父接口判断：HttpServletRequest/HttpServletResponse 都实现了它们
            if (arg instanceof ServletRequest || arg instanceof ServletResponse
                    || arg instanceof MultipartFile) {
                continue;
            }
            serializable.add(arg);
        }
        if (serializable.isEmpty()) {
            return "";
        }
        return objectMapper.writeValueAsString(serializable);
    }

    /**
     * 取当前登录用户的用户名。
     *
     * <p>优先读 Sa-Token 的 Account-Session（见 {@link #SESSION_KEY_USERNAME} 的说明：
     * 按现有登录约定通常读不到），取不到就返回空串，绝不抛异常 ——
     * username 的权威来源是 {@code sys_user.username}，由 vcp-system 侧的落库监听器
     * 按 userId 反查回填（表注释：冗余留存是为了用户改名后仍可追溯）。
     *
     * @return 用户名；未登录或会话中没有该属性时为空串
     */
    private String currentUsername() {
        // isCreate 传 false：只读不新建，避免给每个请求都凭空造一个 Session
        SaSession session = StpUtil.getSession(false);
        if (session == null) {
            return "";
        }
        Object username = session.get(SESSION_KEY_USERNAME);
        return username == null ? "" : String.valueOf(username);
    }

    /**
     * 取客户端来源 IP。
     *
     * <p>非 Web 线程（定时任务、异步线程）没有请求上下文，此时返回空串。
     * 注意这里取的是 {@code getRemoteAddr()}，经 Nginx 等反向代理后会是代理地址，
     * 若将来部署在代理后面需改读 {@code X-Forwarded-For}。
     *
     * @return 来源 IP；无请求上下文时为空串
     */
    private String currentIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest().getRemoteAddr();
        }
        return "";
    }
}
