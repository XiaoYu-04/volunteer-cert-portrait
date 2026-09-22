package com.vcp.system.event;

import com.vcp.framework.log.OperationLogEvent;
import com.vcp.system.entity.OperationLog;
import com.vcp.system.entity.SysUser;
import com.vcp.system.mapper.OperationLogMapper;
import com.vcp.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 操作日志落库监听器：消费 {@link OperationLogEvent}，写进 {@code operation_log} 表。
 *
 * <p><b>为什么是事件而不是切面直接写库</b>：切面在 vcp-framework，而
 * {@code operation_log} 表属于 vcp-system 域。依赖方向是
 * {@code vcp-common → vcp-framework → vcp-system}，vcp-framework 碰不到本模块的
 * Mapper 与实体，反向依赖是被禁止的（见 CLAUDE.md）。按项目约定，
 * 通知类耦合用 Spring Event 解耦，切面只发布事件、不关心谁消费。
 *
 * <p><b>监听器绝不允许抛异常</b>：事件是同步发布的，本方法抛出的异常会沿着
 * {@code publishEvent} 传回切面。虽然切面自己也兜了一层 try-catch，但在这里
 * 直接吞掉更稳妥 —— 日志写不进去是小事，因此让业务接口报错是大事。
 * 同理，失败只记 warn 且不打堆栈，避免日志写不进去时反过来刷爆应用日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogListener {

    private final OperationLogMapper operationLogMapper;

    private final SysUserMapper userMapper;

    /**
     * 落库一条操作日志。
     *
     * @param event 切面采集到的日志事件
     */
    @EventListener
    public void onOperationLog(OperationLogEvent event) {
        try {
            OperationLog entity = new OperationLog();
            entity.setUserId(event.getUserId());
            entity.setUsername(resolveUsername(event));
            entity.setOperation(event.getOperation());
            entity.setModule(event.getModule());
            entity.setAction(event.getAction());
            // target（操作对象描述）暂不采集：切面拿不到「操作了哪条数据」——
            // 它只知道方法签名与请求参数，要拿到业务对象名得让 @OperationLog 支持
            // SpEL 表达式，属于后续增强。列已在 05_backend_gap_fix.sql 里建好，
            // 日志页该列显示为空，不是丢数据。
            entity.setTarget(null);
            entity.setResult(event.getResult());
            entity.setMethod(event.getMethod());
            entity.setParams(event.getParams());
            entity.setIp(event.getIp());
            entity.setCost(event.getCost());
            entity.setCreateTime(event.getCreateTime());
            operationLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("[操作日志] 落库失败，已忽略，不影响业务方法。module={}, action={}",
                    event.getModule(), event.getAction(), e);
        }
    }

    /**
     * 补齐操作人用户名。
     *
     * <p>切面优先从 Sa-Token 会话里取，取不到（未登录、或登录接口失败时还没有会话）
     * 就用 userId 反查 {@code sys_user}。两者都拿不到才留空 —— 登录失败的那条日志
     * 就是这样：连是谁试的都不知道，属于设计使然，登录接口刻意不采集请求参数，
     * 因为请求体里带着明文密码。
     *
     * <p>反查失败只记 warn 并返回空串：用户可能已被删除，而日志本身仍然值得留下。
     *
     * @param event 日志事件
     * @return 用户名；取不到时返回 null
     */
    private String resolveUsername(OperationLogEvent event) {
        if (hasText(event.getUsername())) {
            return event.getUsername();
        }
        if (event.getUserId() == null) {
            return null;
        }
        try {
            SysUser user = userMapper.selectById(event.getUserId());
            return user == null ? null : user.getUsername();
        } catch (Exception e) {
            log.warn("[操作日志] 反查用户名失败。userId={}", event.getUserId(), e);
            return null;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
