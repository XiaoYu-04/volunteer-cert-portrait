package com.vcp.volunteer.service;

import com.vcp.common.result.PageResult;
import com.vcp.volunteer.dto.SignupAuditDTO;
import com.vcp.volunteer.dto.SignupCreateDTO;
import com.vcp.volunteer.dto.SignupQuery;
import com.vcp.volunteer.vo.SignupVO;

/**
 * 活动报名服务。
 *
 * <p><b>报名即占名额</b>（含待审核的报名），与前端 mock 的 enrolled 口径一致：
 * 提交报名时用一条原子 UPDATE 占位，影响行数为 0 即名额已满（待办 A3 的结论，
 * 不先查后写）；驳回与取消时释放名额；审核通过不再加计数，否则同一份报名会被数两次。
 */
public interface SignupService {

    /**
     * 分页查询报名记录。
     *
     * <p>数据范围由登录态决定：学生只看自己的，组织管理员只看本组织活动的，
     * 学校管理员看全量。前端传来的 studentId / orgId 不能作为数据范围依据。
     *
     * @param query 筛选条件
     * @return 报名分页结果
     */
    PageResult<SignupVO> listSignups(SignupQuery query);

    /**
     * 学生提交报名。
     *
     * @param dto 活动 id 与报名理由；studentId 由登录态决定，请求体里的值忽略
     * @return 报名 id
     * @throws com.vcp.common.exception.BusinessException 活动不存在（30001）、不接受报名（30005）、
     *                                                    名额已满（30006）、重复报名（30007）
     */
    Long createSignup(SignupCreateDTO dto);

    /**
     * 审核报名。
     *
     * <p>通过时顺带生成签到记录（未签到）并给学生发一条通知；驳回时必须填理由，
     * 并释放一个名额。
     *
     * @param id  报名 id
     * @param dto 审核动作与备注
     * @throws com.vcp.common.exception.BusinessException 报名不存在（30008）、已审核（30009）、
     *                                                    动作或理由不合法（10001）
     */
    void auditSignup(Long id, SignupAuditDTO dto);

    /**
     * 学生取消报名。
     *
     * <p>取消是「改状态复用同一行」，不做逻辑删除：表上有 uk_activity_student 唯一约束，
     * 软删除后同一学生重新报名会直接撞唯一键（待办 A7 的结论）。同时释放名额，
     * 并把该报名已生成的签到记录作废（同样保留行，等重新报名通过时复用）。
     *
     * @param id 报名 id
     * @throws com.vcp.common.exception.BusinessException 报名不存在或不属于当前学生（30008）、
     *                                                    已完成的活动无法取消（30010）
     */
    void cancelSignup(Long id);
}
