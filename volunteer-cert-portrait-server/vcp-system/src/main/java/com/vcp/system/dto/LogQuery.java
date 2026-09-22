package com.vcp.system.dto;

import com.vcp.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志查询条件（GET /api/v1/system/logs）。
 *
 * <p>module 是<b>中文全等匹配</b>：日志页的筛选项写死了
 * 「时长认证 / 志愿活动 / 活动报名 / 用户与权限 / 签到签退 / 志愿组织 / 认证」，
 * 因此入库的 module 必须逐字相同，写错不报错但永远筛不出来。
 * 用 like 而不用 eq 会让"时长认证"误命中"时长认证xxx"，故此处必须是全等。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LogQuery extends PageQuery {

    /** 关键字，同时匹配操作人与操作对象 */
    private String keyword;

    /** 所属模块，中文全等匹配 */
    private String module;
}
