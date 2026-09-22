package com.vcp.system.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 操作日志响应对象。
 *
 * <p>字段与日志页的 8 个表格列一一对应：
 * time / operator / role / action / module / target / ip / result。
 * 页面直接渲染 time 与 ip（{{ row.time }}），因此 time 必须是已格式化的
 * "yyyy-MM-dd HH:mm:ss" 字符串。
 *
 * <p><b>role 是推导出来的</b>，不是 operation_log 表的列：
 * 由 user_id 经 sys_user_role → sys_role 实时查出，这样管理员改角色后
 * 历史日志的角色列会跟着更新，不会停留在旧角色上。用户已被删除或
 * 查不到角色时返回 null，页面显示空白。
 *
 * <p><b>target 当前恒为空</b>：操作切面还没有采集"操作对象"，
 * 列已建好（见 sql/05_backend_gap_fix.sql），等 @OperationLog 注解
 * 补上 target 属性后即可填充。
 */
@Data
public class OperationLogVO implements Serializable {

    private Long id;

    /** 操作时间，已格式化为 yyyy-MM-dd HH:mm:ss */
    private String time;

    /** 操作人用户名 */
    private String operator;

    /** 操作人角色中文名，由 user_id 关联推导 */
    private String role;

    /** 动作描述 */
    private String action;

    /** 所属模块，中文 */
    private String module;

    /** 操作对象描述；当前切面未采集，恒为空 */
    private String target;

    private String ip;

    /** 执行结果：SUCCESS / FAIL */
    private String result;
}
