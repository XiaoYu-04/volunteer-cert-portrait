package com.vcp.volunteer.dto;

import com.vcp.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报名列表查询条件（GET /api/v1/signups）。
 *
 * <p>三个角色共用同一个接口，数据范围一律由登录态决定（见待办 B19）：
 * <ul>
 *   <li>学生：只看自己的报名，<b>忽略前端传来的 studentId</b>；</li>
 *   <li>组织管理员：只看本组织活动的报名，忽略前端传来的 orgId；</li>
 *   <li>学校管理员：全量。</li>
 * </ul>
 * 若照搬前端参数，任意学生改一个 studentId 就能读到别人的报名记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SignupQuery extends PageQuery {

    /** 关键字，匹配学生姓名、学号或活动名称 */
    private String keyword;

    /** 报名状态码，全等匹配 */
    private String status;

    /** 活动 id */
    private Long activityId;

    /** 学生档案 id；学生角色下该参数被登录态覆盖 */
    private Long studentId;

    /** 组织 id；组织管理员下该参数被登录态覆盖 */
    private Long orgId;

    /** 学院，全等匹配 */
    private String college;
}
