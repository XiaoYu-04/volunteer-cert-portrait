package com.vcp.framework.log;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解，标注在 Controller 方法上，由 {@link OperationLogAspect} 拦截采集。
 *
 * <p>只标"写"操作（新增/修改/删除/审核/提交等有审计价值的动作），查询接口不标，
 * 否则日志表会被无意义的读操作淹没。标在 Controller 方法上而不是 Service 上：
 * 只有 Controller 层才拿得到 HttpServletRequest（来源 IP）与登录态。
 *
 * <p><b>{@link #module()} 的取值必须从前端日志页的筛选项里选</b>：
 * {@code src/views/admin/LogView.vue} 里写死了
 * {@code ['时长认证', '志愿活动', '活动报名', '用户与权限', '签到签退', '志愿组织', '认证']}，
 * 且前端是<b>中文全等匹配</b>筛选。这里传别的字串（比如英文码、简称）不会报错，
 * 但用户在日志页按模块筛选时永远筛不出来，属于静默失效。
 *
 * <p>注意本注解与 {@code operation_log} 表的实体类同名不同包：本类在
 * {@code com.vcp.framework.log}（注解），实体在 {@code com.vcp.system} 域。
 * 在 vcp-system 里同时用到两者时注意区分，必要时写全限定名。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OperationLog {

    /**
     * 模块名，必须是前端日志页筛选项里的中文名（如「志愿活动」「时长认证」）。
     *
     * @return 模块名
     */
    String module();

    /**
     * 动作描述，与模块名拼成入库的 {@code operation} 字段（如「发布活动」「审核时长」）。
     *
     * @return 动作描述
     */
    String action();
}