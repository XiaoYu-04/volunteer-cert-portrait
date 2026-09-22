package com.vcp.framework.log;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 操作日志事件：承载 {@link OperationLogAspect} 采集到的一条日志数据，由 vcp-system 的监听器落库。
 *
 * <p>字段按 {@code operation_log} 表的<b>现有列</b>设计（user_id / username / operation /
 * method / params / ip / create_time），保证监听器可以直接映射入库，不需要再做转换。
 * 时间字段类型用 {@link LocalDateTime}，与 {@code BaseEntity.createTime} 及库里的
 * {@code TIMESTAMP}（无时区）保持一致。
 *
 * <p><b>B16 扩表已落地</b>：{@code operation_log} 表原先缺 {@code role} / {@code module} /
 * {@code target} / {@code result} 四列，而前端日志页（{@code src/views/admin/LogView.vue}）
 * 这四列都要展示，且 {@code module} 还是中文全等筛选。现已在
 * {@code sql/05_backend_gap_fix.sql} 中补齐 {@code module} / {@code action} / {@code target} /
 * {@code result} / {@code cost} 五列，本类随之带上 {@code module} / {@code action} /
 * {@code result} 三个字段。
 *
 * <p>{@code role} 不落列：它由 {@code user_id} 关联 {@code sys_user_role → sys_role} 推导，
 * 冗余存储会在角色调整后失真。{@code target}（操作对象描述）切面仍然采集不到，
 * 落库时留空，待 {@code @OperationLog} 增加 target 属性后再填。
 *
 * <p>本类之所以做成事件而不是让切面直接写库：{@code operation_log} 表归属 vcp-system 域，
 * 而切面所在模块的依赖方向是 {@code vcp-common → vcp-framework → vcp-system}，
 * vcp-framework <b>不能反向依赖</b> vcp-system，也就碰不到该表的 Mapper/实体。
 * 按项目约定「通知类耦合用 Spring Event 解耦」，切面只负责发布事件。
 */
@Getter
public class OperationLogEvent extends ApplicationEvent {

    /** 操作人 ID；未登录（如开放接口）时为 {@code null} */
    private final Long userId;

    /** 操作人用户名；未登录或会话里没有该属性时为空串 */
    private final String username;

    /** 操作描述，由注解的「模块名 + 动作」拼成，如「志愿活动 - 发布活动」 */
    private final String operation;

    /** 所属模块，直接取自注解的 {@code module()}，与日志页的筛选项逐字一致 */
    private final String module;

    /** 动作描述，直接取自注解的 {@code action()}，如「发布活动」 */
    private final String action;

    /** 执行结果：{@code SUCCESS} 业务方法正常返回，{@code FAIL} 抛出异常 */
    private final String result;

    /** 被调用的方法签名，如 {@code ActivityController.publishActivity(..)} */
    private final String method;

    /** 请求参数的 JSON 串；无参数或全部被过滤时为空串 */
    private final String params;

    /** 来源 IP；非 Web 线程（如定时任务）触发时为空串 */
    private final String ip;

    /** 日志产生时间 */
    private final LocalDateTime createTime;

    /**
     * 业务方法耗时（毫秒）。
     *
     * <p>{@code operation_log} 表目前<b>没有</b>对应列，该值只随事件传递，
     * 落库时忽略即可；保留它是为了排查慢接口，也便于 B16 扩表后直接落库。
     */
    private final long cost;

    /**
     * 构造一条操作日志事件。
     *
     * @param source     事件来源，切面传入自身实例，仅用于标识发布者
     * @param userId     操作人 ID，未登录传 {@code null}
     * @param username   操作人用户名，未知传空串
     * @param operation  操作描述（模块 + 动作），供 {@code operation} 列展示
     * @param module     所属模块，取自注解 {@code module()}
     * @param action     动作描述，取自注解 {@code action()}
     * @param result     执行结果，{@code SUCCESS} 或 {@code FAIL}
     * @param method     方法签名
     * @param params     请求参数 JSON
     * @param ip         来源 IP
     * @param createTime 日志产生时间
     * @param cost       业务方法耗时（毫秒）
     */
    public OperationLogEvent(Object source, Long userId, String username, String operation,
                             String module, String action, String result,
                             String method, String params, String ip,
                             LocalDateTime createTime, long cost) {
        super(source);
        this.userId = userId;
        this.username = username;
        this.operation = operation;
        this.module = module;
        this.action = action;
        this.result = result;
        this.method = method;
        this.params = params;
        this.ip = ip;
        this.createTime = createTime;
        this.cost = cost;
    }
}
