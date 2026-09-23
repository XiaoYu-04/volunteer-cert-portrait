package com.vcp.volunteer.dto;

import com.vcp.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 活动列表查询条件（GET /api/v1/activities）。
 *
 * <p>字段名与前端 {@code ActivityListView} / {@code ActivityManageView} 的筛选表单一致：
 * keyword（活动名称）、type（<b>分类名称</b>，不是分类 id）、status、orgId、onlyOpen。
 * 前端「重置」会把它们置为空串，因此 Service 里一律用 hasText 判断后再拼条件（见待办 B19）。
 *
 * <p><b>orgId 的数据范围</b>：组织管理员只会看到本组织的活动，Service 里会用登录态里的
 * orgId 覆盖该参数，防止改一个查询串就读到别人组织的草稿。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityQuery extends PageQuery {

    /** 关键字，匹配活动名称 */
    private String keyword;

    /** 活动分类名称，全等匹配 */
    private String type;

    /** 活动状态码，全等匹配 */
    private String status;

    /** 发布组织 id */
    private Long orgId;

    /** 只看可报名的活动（已发布且未报满），前端传 "true" */
    private Boolean onlyOpen;
}
