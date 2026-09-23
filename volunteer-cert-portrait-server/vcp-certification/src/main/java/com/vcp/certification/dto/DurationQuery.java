package com.vcp.certification.dto;

import com.vcp.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 服务时长列表查询条件。
 *
 * <p>分页参数 page / pageSize 由父类提供，字段名不可改（前端 useTable 统一发这两个键）。
 *
 * <p><b>orgId / studentId 两个参数只对学校管理员生效</b>：前端把这两个 id 当普通查询参数
 * 自己传（组织端传本组织、学生端传本人），mock 里条件为空就直接返回全量。
 * 后端必须以登录态为准 —— 组织管理员只认会话里的 orgId、学生只认会话里的 studentId，
 * 忽略请求里传的同名参数，否则任意学生改一个 id 就能查别人的时长（见待办 B19）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DurationQuery extends PageQuery {

    /** 关键字，同时匹配学生姓名与活动名称 */
    private String keyword;

    /** 状态英文码：PENDING_SUBMIT / PENDING_AUDIT / APPROVED / REJECTED */
    private String status;

    /** 学院精确筛选 */
    private String college;

    /** 组织筛选，仅学校管理员生效 */
    private Long orgId;

    /** 学生筛选，仅学校管理员生效 */
    private Long studentId;
}
